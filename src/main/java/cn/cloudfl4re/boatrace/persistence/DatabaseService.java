package cn.cloudfl4re.boatrace.persistence;

import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.PersonalTrialStat;
import cn.cloudfl4re.boatrace.model.PlayerPenalty;
import cn.cloudfl4re.boatrace.model.RaceResultEntry;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.model.TrialRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseService {
    private final Path databasePath;
    private final Logger logger;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Connection connection;

    public DatabaseService(Path dataFolder, int queueCapacity, Logger logger) {
        this.databasePath = dataFolder.resolve("boatrace.db");
        this.logger = logger;
        this.executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(32, queueCapacity)),
            Thread.ofPlatform().name("BoatRace-Database").daemon(true).factory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public CompletableFuture<LoadedData> initialize() {
        return submit(() -> {
            Files.createDirectories(databasePath.getParent());
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            createSchema();
            return loadAll();
        });
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_meta (version INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO schema_meta(version) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM schema_meta)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS tracks (id TEXT PRIMARY KEY, display_name TEXT NOT NULL, world_uuid TEXT NOT NULL, start_min_x REAL NOT NULL, start_min_y REAL NOT NULL, start_min_z REAL NOT NULL, start_max_x REAL NOT NULL, start_max_y REAL NOT NULL, start_max_z REAL NOT NULL, updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS checkpoints (track_id TEXT NOT NULL, position INTEGER NOT NULL, min_x REAL NOT NULL, min_y REAL NOT NULL, min_z REAL NOT NULL, max_x REAL NOT NULL, max_y REAL NOT NULL, max_z REAL NOT NULL, PRIMARY KEY(track_id, position), FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS start_slots (track_id TEXT NOT NULL, position INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, PRIMARY KEY(track_id, position), FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS trial_bests (track_id TEXT NOT NULL, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, best_nanos INTEGER NOT NULL, achieved_at INTEGER NOT NULL, PRIMARY KEY(track_id, player_uuid), FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_trial_bests_rank ON trial_bests(track_id, best_nanos, achieved_at)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS last_races (track_id TEXT PRIMARY KEY, code TEXT NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL, FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS last_race_results (track_id TEXT NOT NULL, position INTEGER NOT NULL, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, rank_value INTEGER NOT NULL, elapsed_nanos INTEGER NOT NULL, finished INTEGER NOT NULL, completed_laps INTEGER NOT NULL DEFAULT 0, total_laps INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(track_id, position), FOREIGN KEY(track_id) REFERENCES last_races(track_id) ON DELETE CASCADE)");
            addColumnIfMissing(statement, "last_race_results", "completed_laps", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "last_race_results", "total_laps", "INTEGER NOT NULL DEFAULT 0");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS owned_boats (entity_uuid TEXT PRIMARY KEY, created_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_penalties (player_uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, violation_count INTEGER NOT NULL DEFAULT 0, cooldown_until INTEGER NOT NULL DEFAULT 0, admin_banned INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
        }
    }

    private static void addColumnIfMissing(Statement statement, String table, String column, String definition) {
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
        }
    }

    private LoadedData loadAll() throws SQLException {
        Map<String, TrackBuilder> builders = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM tracks ORDER BY id")) {
            while (result.next()) {
                String id = result.getString("id");
                Cuboid start = new Cuboid(
                    result.getDouble("start_min_x"),
                    result.getDouble("start_min_y"),
                    result.getDouble("start_min_z"),
                    result.getDouble("start_max_x"),
                    result.getDouble("start_max_y"),
                    result.getDouble("start_max_z")
                );
                builders.put(id, new TrackBuilder(id, result.getString("display_name"), UUID.fromString(result.getString("world_uuid")), start));
            }
        }
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM checkpoints ORDER BY track_id, position")) {
            while (result.next()) {
                TrackBuilder builder = builders.get(result.getString("track_id"));
                if (builder != null) {
                    builder.checkpoints.add(new Cuboid(
                        result.getDouble("min_x"),
                        result.getDouble("min_y"),
                        result.getDouble("min_z"),
                        result.getDouble("max_x"),
                        result.getDouble("max_y"),
                        result.getDouble("max_z")
                    ));
                }
            }
        }
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM start_slots ORDER BY track_id, position")) {
            while (result.next()) {
                TrackBuilder builder = builders.get(result.getString("track_id"));
                if (builder != null) {
                    builder.slots.add(new StartSlot(
                        result.getDouble("x"),
                        result.getDouble("y"),
                        result.getDouble("z"),
                        result.getFloat("yaw"),
                        result.getFloat("pitch")
                    ));
                }
            }
        }
        Map<String, Track> tracks = new LinkedHashMap<>();
        builders.forEach((id, builder) -> tracks.put(id, builder.build()));
        Map<String, List<TrialRecord>> leaderboards = loadLeaderboards();
        Map<String, Integer> leaderboardRecordCounts = loadLeaderboardRecordCounts();
        Map<String, LastRace> lastRaces = loadLastRaces();
        Set<UUID> ownedBoats = new HashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT entity_uuid FROM owned_boats")) {
            while (result.next()) {
                ownedBoats.add(UUID.fromString(result.getString(1)));
            }
        }
        Map<UUID, PlayerPenalty> penalties = loadPenalties();
        return new LoadedData(Map.copyOf(tracks), immutableLists(leaderboards), Map.copyOf(leaderboardRecordCounts), Map.copyOf(lastRaces), Set.copyOf(ownedBoats), Map.copyOf(penalties));
    }

    private Map<UUID, PlayerPenalty> loadPenalties() throws SQLException {
        Map<UUID, PlayerPenalty> values = new HashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT player_uuid, player_name, violation_count, cooldown_until, admin_banned FROM player_penalties")) {
            while (result.next()) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(result.getString("player_uuid"));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                values.put(playerId, new PlayerPenalty(
                    playerId,
                    result.getString("player_name"),
                    result.getInt("violation_count"),
                    result.getLong("cooldown_until"),
                    result.getInt("admin_banned") != 0
                ));
            }
        }
        return values;
    }

    private Map<String, List<TrialRecord>> loadLeaderboards() throws SQLException {
        Map<String, List<TrialRecord>> values = new HashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT track_id, player_uuid, player_name, best_nanos, achieved_at FROM trial_bests ORDER BY track_id, best_nanos, achieved_at, player_uuid")) {
            while (result.next()) {
                List<TrialRecord> records = values.computeIfAbsent(result.getString("track_id"), ignored -> new ArrayList<>());
                if (records.size() < 15) {
                    records.add(new TrialRecord(
                        UUID.fromString(result.getString("player_uuid")),
                        result.getString("player_name"),
                        result.getLong("best_nanos"),
                        result.getLong("achieved_at")
                    ));
                }
            }
        }
        return values;
    }

    private Map<String, Integer> loadLeaderboardRecordCounts() throws SQLException {
        Map<String, Integer> values = new HashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT track_id, COUNT(*) FROM trial_bests GROUP BY track_id")) {
            while (result.next()) {
                values.put(result.getString(1), result.getInt(2));
            }
        }
        return values;
    }

    private Map<String, LastRace> loadLastRaces() throws SQLException {
        Map<String, RaceHeader> headers = new HashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM last_races")) {
            while (result.next()) {
                headers.put(result.getString("track_id"), new RaceHeader(
                    result.getString("code"),
                    result.getLong("started_at"),
                    result.getLong("ended_at")
                ));
            }
        }
        Map<String, List<RaceResultEntry>> entries = new HashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM last_race_results ORDER BY track_id, position")) {
            while (result.next()) {
                entries.computeIfAbsent(result.getString("track_id"), ignored -> new ArrayList<>()).add(new RaceResultEntry(
                    UUID.fromString(result.getString("player_uuid")),
                    result.getString("player_name"),
                    result.getInt("rank_value"),
                    result.getLong("elapsed_nanos"),
                    result.getInt("finished") != 0,
                    result.getInt("completed_laps"),
                    result.getInt("total_laps")
                ));
            }
        }
        Map<String, LastRace> races = new HashMap<>();
        headers.forEach((trackId, header) -> races.put(trackId, new LastRace(
            trackId,
            header.code,
            header.startedAt,
            header.endedAt,
            entries.getOrDefault(trackId, List.of())
        )));
        return races;
    }

    private static Map<String, List<TrialRecord>> immutableLists(Map<String, List<TrialRecord>> source) {
        Map<String, List<TrialRecord>> values = new HashMap<>();
        source.forEach((key, value) -> values.put(key, List.copyOf(value)));
        return Map.copyOf(values);
    }

    public CompletableFuture<Void> saveTrack(Track track) {
        return submit(() -> {
            boolean original = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO tracks(id, display_name, world_uuid, start_min_x, start_min_y, start_min_z, start_max_x, start_max_y, start_max_z, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name, world_uuid=excluded.world_uuid, start_min_x=excluded.start_min_x, start_min_y=excluded.start_min_y, start_min_z=excluded.start_min_z, start_max_x=excluded.start_max_x, start_max_y=excluded.start_max_y, start_max_z=excluded.start_max_z, updated_at=excluded.updated_at")) {
                    statement.setString(1, track.id());
                    statement.setString(2, track.displayName());
                    statement.setString(3, track.worldId().toString());
                    bindCuboid(statement, 4, track.start());
                    statement.setLong(10, System.currentTimeMillis());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM checkpoints WHERE track_id=?")) {
                    statement.setString(1, track.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO checkpoints(track_id, position, min_x, min_y, min_z, max_x, max_y, max_z) VALUES(?,?,?,?,?,?,?,?)")) {
                    for (int index = 0; index < track.checkpoints().size(); index++) {
                        statement.setString(1, track.id());
                        statement.setInt(2, index);
                        bindCuboid(statement, 3, track.checkpoints().get(index));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM start_slots WHERE track_id=?")) {
                    statement.setString(1, track.id());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO start_slots(track_id, position, x, y, z, yaw, pitch) VALUES(?,?,?,?,?,?,?)")) {
                    for (int index = 0; index < track.slots().size(); index++) {
                        StartSlot slot = track.slots().get(index);
                        statement.setString(1, track.id());
                        statement.setInt(2, index);
                        statement.setDouble(3, slot.x());
                        statement.setDouble(4, slot.y());
                        statement.setDouble(5, slot.z());
                        statement.setFloat(6, slot.yaw());
                        statement.setFloat(7, slot.pitch());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (Throwable throwable) {
                connection.rollback();
                throw throwable;
            } finally {
                connection.setAutoCommit(original);
            }
            return null;
        });
    }

    private static void bindCuboid(PreparedStatement statement, int start, Cuboid cuboid) throws SQLException {
        statement.setDouble(start, cuboid.minX());
        statement.setDouble(start + 1, cuboid.minY());
        statement.setDouble(start + 2, cuboid.minZ());
        statement.setDouble(start + 3, cuboid.maxX());
        statement.setDouble(start + 4, cuboid.maxY());
        statement.setDouble(start + 5, cuboid.maxZ());
    }

    public CompletableFuture<Void> deleteTrack(String trackId) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM tracks WHERE id=?")) {
                statement.setString(1, trackId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<TrialSaveResult> recordTrial(String trackId, UUID playerId, String playerName, long nanos, long achievedAt) {
        return submit(() -> {
            boolean original = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long previous = -1L;
                try (PreparedStatement statement = connection.prepareStatement("SELECT best_nanos FROM trial_bests WHERE track_id=? AND player_uuid=?")) {
                    statement.setString(1, trackId);
                    statement.setString(2, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            previous = result.getLong(1);
                        }
                    }
                }
                boolean personalBest = previous < 0L || nanos < previous;
                if (personalBest) {
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO trial_bests(track_id, player_uuid, player_name, best_nanos, achieved_at) VALUES(?,?,?,?,?) ON CONFLICT(track_id, player_uuid) DO UPDATE SET player_name=excluded.player_name, best_nanos=excluded.best_nanos, achieved_at=excluded.achieved_at")) {
                        statement.setString(1, trackId);
                        statement.setString(2, playerId.toString());
                        statement.setString(3, playerName);
                        statement.setLong(4, nanos);
                        statement.setLong(5, achievedAt);
                        statement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE trial_bests SET player_name=? WHERE track_id=? AND player_uuid=?")) {
                        statement.setString(1, playerName);
                        statement.setString(2, trackId);
                        statement.setString(3, playerId.toString());
                        statement.executeUpdate();
                    }
                }
                List<TrialRecord> top = queryTopFifteen(trackId);
                int recordCount = queryRecordCount(trackId);
                connection.commit();
                return new TrialSaveResult(personalBest, previous, top, recordCount);
            } catch (Throwable throwable) {
                connection.rollback();
                throw throwable;
            } finally {
                connection.setAutoCommit(original);
            }
        });
    }

    public CompletableFuture<TrialDeleteResult> deleteTrialRecord(String trackId, String playerReference) {
        return submit(() -> {
            boolean original = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String reference = playerReference == null ? "" : playerReference.trim();
                String uuidReference;
                try {
                    uuidReference = UUID.fromString(reference).toString();
                } catch (IllegalArgumentException exception) {
                    uuidReference = "";
                }
                String playerUuid = null;
                try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_uuid FROM trial_bests WHERE track_id=? AND (player_uuid=? OR player_name COLLATE NOCASE=?) ORDER BY best_nanos, achieved_at, player_uuid LIMIT 1"
                )) {
                    statement.setString(1, trackId);
                    statement.setString(2, uuidReference);
                    statement.setString(3, reference);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            playerUuid = result.getString(1);
                        }
                    }
                }
                int deleted = 0;
                if (playerUuid != null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM trial_bests WHERE track_id=? AND player_uuid=?"
                    )) {
                        statement.setString(1, trackId);
                        statement.setString(2, playerUuid);
                        deleted = statement.executeUpdate();
                    }
                }
                List<TrialRecord> top = queryTopFifteen(trackId);
                int recordCount = queryRecordCount(trackId);
                connection.commit();
                return new TrialDeleteResult(deleted, top, recordCount);
            } catch (Throwable throwable) {
                connection.rollback();
                throw throwable;
            } finally {
                connection.setAutoCommit(original);
            }
        });
    }

    private List<TrialRecord> queryTopFifteen(String trackId) throws SQLException {
        List<TrialRecord> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid, player_name, best_nanos, achieved_at FROM trial_bests WHERE track_id=? ORDER BY best_nanos, achieved_at, player_uuid LIMIT 15")) {
            statement.setString(1, trackId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new TrialRecord(
                        UUID.fromString(result.getString("player_uuid")),
                        result.getString("player_name"),
                        result.getLong("best_nanos"),
                        result.getLong("achieved_at")
                    ));
                }
            }
        }
        return List.copyOf(values);
    }

    private int queryRecordCount(String trackId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM trial_bests WHERE track_id=?")) {
            statement.setString(1, trackId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /**
     * Resolves a player's own trial best on a track together with its rank. Completes with
     * {@code null} when the player has no record there.
     */
    public CompletableFuture<PersonalTrialStat> findPersonalTrial(String trackId, UUID playerId) {
        return submit(() -> {
            String playerUuid = playerId.toString();
            String playerName = null;
            long bestNanos = 0L;
            long achievedAt = 0L;
            try (PreparedStatement statement = connection.prepareStatement("SELECT player_name, best_nanos, achieved_at FROM trial_bests WHERE track_id=? AND player_uuid=?")) {
                statement.setString(1, trackId);
                statement.setString(2, playerUuid);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        playerName = result.getString(1);
                        bestNanos = result.getLong(2);
                        achievedAt = result.getLong(3);
                    }
                }
            }
            if (playerName == null) {
                return null;
            }
            // Mirrors the ORDER BY used by the leaderboard queries so both agree on ties.
            int ahead = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM trial_bests WHERE track_id=? AND (best_nanos<? OR (best_nanos=? AND (achieved_at<? OR (achieved_at=? AND player_uuid<?))))"
            )) {
                statement.setString(1, trackId);
                statement.setLong(2, bestNanos);
                statement.setLong(3, bestNanos);
                statement.setLong(4, achievedAt);
                statement.setLong(5, achievedAt);
                statement.setString(6, playerUuid);
                try (ResultSet result = statement.executeQuery()) {
                    ahead = result.next() ? result.getInt(1) : 0;
                }
            }
            return new PersonalTrialStat(trackId, playerId, playerName, bestNanos, achievedAt, ahead + 1, queryRecordCount(trackId));
        });
    }

    public CompletableFuture<Void> saveLastRace(LastRace race) {
        return submit(() -> {
            boolean original = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO last_races(track_id, code, started_at, ended_at) VALUES(?,?,?,?) ON CONFLICT(track_id) DO UPDATE SET code=excluded.code, started_at=excluded.started_at, ended_at=excluded.ended_at")) {
                    statement.setString(1, race.trackId());
                    statement.setString(2, race.code());
                    statement.setLong(3, race.startedEpochMillis());
                    statement.setLong(4, race.endedEpochMillis());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM last_race_results WHERE track_id=?")) {
                    statement.setString(1, race.trackId());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO last_race_results(track_id, position, player_uuid, player_name, rank_value, elapsed_nanos, finished, completed_laps, total_laps) VALUES(?,?,?,?,?,?,?,?,?)")) {
                    for (int index = 0; index < race.entries().size(); index++) {
                        RaceResultEntry entry = race.entries().get(index);
                        statement.setString(1, race.trackId());
                        statement.setInt(2, index);
                        statement.setString(3, entry.playerId().toString());
                        statement.setString(4, entry.playerName());
                        statement.setInt(5, entry.rank());
                        statement.setLong(6, entry.elapsedNanos());
                        statement.setInt(7, entry.finished() ? 1 : 0);
                        statement.setInt(8, entry.completedLaps());
                        statement.setInt(9, entry.totalLaps());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (Throwable throwable) {
                connection.rollback();
                throw throwable;
            } finally {
                connection.setAutoCommit(original);
            }
            return null;
        });
    }

    public CompletableFuture<Void> registerBoat(UUID boatId) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO owned_boats(entity_uuid, created_at) VALUES(?,?)")) {
                statement.setString(1, boatId.toString());
                statement.setLong(2, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> unregisterBoat(UUID boatId) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM owned_boats WHERE entity_uuid=?")) {
                statement.setString(1, boatId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> savePenalty(PlayerPenalty penalty) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_penalties(player_uuid, player_name, violation_count, cooldown_until, admin_banned, updated_at) VALUES(?,?,?,?,?,?) "
                    + "ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name, violation_count=excluded.violation_count, cooldown_until=excluded.cooldown_until, admin_banned=excluded.admin_banned, updated_at=excluded.updated_at"
            )) {
                statement.setString(1, penalty.playerId().toString());
                statement.setString(2, penalty.playerName());
                statement.setInt(3, penalty.violationCount());
                statement.setLong(4, penalty.cooldownUntilEpochMillis());
                statement.setInt(5, penalty.adminBanned() ? 1 : 0);
                statement.setLong(6, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> deletePenalty(UUID playerId) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM player_penalties WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<UUID> findPlayerIdByName(String playerName) {
        return submit(() -> {
            String reference = playerName == null ? "" : playerName.trim();
            if (reference.isEmpty()) {
                return null;
            }
            String[] queries = {
                "SELECT player_uuid FROM player_penalties WHERE player_name COLLATE NOCASE=? LIMIT 1",
                "SELECT player_uuid FROM trial_bests WHERE player_name COLLATE NOCASE=? LIMIT 1",
                "SELECT player_uuid FROM last_race_results WHERE player_name COLLATE NOCASE=? LIMIT 1"
            };
            for (String query : queries) {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, reference);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            try {
                                return UUID.fromString(result.getString(1));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            }
            return null;
        });
    }

    public CompletableFuture<Void> clearOwnedBoats(Set<UUID> boatIds) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM owned_boats WHERE entity_uuid=?")) {
                for (UUID boatId : boatIds) {
                    statement.setString(1, boatId.toString());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> submit(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed.get()) {
            future.completeExceptionally(new IllegalStateException("Database is closed"));
            return future;
        }
        try {
            executor.execute(() -> {
                try {
                    future.complete(callable.call());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RejectedExecutionException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Future<?> closeTask;
        try {
            closeTask = executor.submit(() -> {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException exception) {
                        logger.log(Level.SEVERE, "Failed to close BoatRace database", exception);
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            closeTask = null;
        }
        executor.shutdown();
        if (closeTask != null) {
            try {
                closeTask.get(5L, TimeUnit.SECONDS);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "BoatRace database close timed out", exception);
            }
        }
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static final class TrackBuilder {
        private final String id;
        private final String displayName;
        private final UUID worldId;
        private final Cuboid start;
        private final List<Cuboid> checkpoints = new ArrayList<>();
        private final List<StartSlot> slots = new ArrayList<>();

        private TrackBuilder(String id, String displayName, UUID worldId, Cuboid start) {
            this.id = id;
            this.displayName = displayName;
            this.worldId = worldId;
            this.start = start;
        }

        private Track build() {
            return new Track(id, displayName, worldId, start, checkpoints, slots);
        }
    }

    private record RaceHeader(String code, long startedAt, long endedAt) {
    }
}
