package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.config.MessageService;
import cn.cloudfl4re.boatrace.config.PluginSettings;
import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.ParticipantProgress;
import cn.cloudfl4re.boatrace.model.ParticipantStatus;
import cn.cloudfl4re.boatrace.model.PlayerPenalty;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.RacePhase;
import cn.cloudfl4re.boatrace.model.RaceResultEntry;
import cn.cloudfl4re.boatrace.model.RaceSession;
import cn.cloudfl4re.boatrace.model.RoutePenalty;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.StoredLocation;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.model.TrialRun;
import cn.cloudfl4re.boatrace.persistence.DatabaseService;
import cn.cloudfl4re.boatrace.persistence.TrialSaveResult;
import cn.cloudfl4re.boatrace.scheduler.SchedulerFacade;
import cn.cloudfl4re.boatrace.scheduler.TaskHandle;
import cn.cloudfl4re.boatrace.util.RaceCodeGenerator;
import cn.cloudfl4re.boatrace.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class RaceManager {
    public static final int MAX_FORMAL_LAPS = 1000;

    private final Plugin plugin;
    private final TrackService tracks;
    private final LeaderboardService leaderboards;
    private final LastRaceService lastRaces;
    private final DatabaseService database;
    private final PenaltyService penalties;
    private final PersonalStatsService personalStats;
    private final SchedulerFacade scheduler;
    private final MessageService messages;
    private final Supplier<PluginSettings> settings;
    private final ParticleRenderer particles;
    private final NamespacedKey boatKey;
    private final RaceCodeGenerator codeGenerator = new RaceCodeGenerator();
    private final ConcurrentHashMap<String, RaceSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionByTrack = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> sessionByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> pendingJoinTeleports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TrialRun> trials = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TaskHandle> uiTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, StoredLocation> stagingOrigins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, StartSlot> stagingAssignments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> stagingRemaining = new ConcurrentHashMap<>();
    private final Set<String> stagingFailures = ConcurrentHashMap.newKeySet();
    private final Set<UUID> stagedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> raceBoats = ConcurrentHashMap.newKeySet();
    private final Set<UUID> stagedCorrections = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, UUID> boatOwners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, RoutePenalty> routePenalties = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> exitMessageTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> penaltyMessageTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> idleParticleTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, StoredLocation> pausedBoatLocations = new ConcurrentHashMap<>();
    private final Set<UUID> pausedCorrections = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, SpectatorState> spectators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> spectatorTargets = new ConcurrentHashMap<>();
    private final Set<String> endingSessions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> antiCheatPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> antiCheatTrials = ConcurrentHashMap.newKeySet();
    private volatile TaskHandle cleanupTask = TaskHandle.NOOP;

    public RaceManager(
        Plugin plugin,
        TrackService tracks,
        LeaderboardService leaderboards,
        LastRaceService lastRaces,
        DatabaseService database,
        SchedulerFacade scheduler,
        MessageService messages,
        Supplier<PluginSettings> settings,
        ParticleRenderer particles
    ) {
        this(plugin, tracks, leaderboards, lastRaces, database, new PenaltyService(database, plugin.getLogger()), scheduler, messages, settings, particles);
    }

    public RaceManager(
        Plugin plugin,
        TrackService tracks,
        LeaderboardService leaderboards,
        LastRaceService lastRaces,
        DatabaseService database,
        PenaltyService penalties,
        SchedulerFacade scheduler,
        MessageService messages,
        Supplier<PluginSettings> settings,
        ParticleRenderer particles
    ) {
        this(plugin, tracks, leaderboards, lastRaces, database, penalties, new PersonalStatsService(database, plugin.getLogger()), scheduler, messages, settings, particles);
    }

    public RaceManager(
        Plugin plugin,
        TrackService tracks,
        LeaderboardService leaderboards,
        LastRaceService lastRaces,
        DatabaseService database,
        PenaltyService penalties,
        PersonalStatsService personalStats,
        SchedulerFacade scheduler,
        MessageService messages,
        Supplier<PluginSettings> settings,
        ParticleRenderer particles
    ) {
        this.plugin = plugin;
        this.tracks = tracks;
        this.leaderboards = leaderboards;
        this.lastRaces = lastRaces;
        this.database = database;
        this.penalties = penalties;
        this.personalStats = personalStats;
        this.scheduler = scheduler;
        this.messages = messages;
        this.settings = settings;
        this.particles = particles;
        this.boatKey = new NamespacedKey(plugin, "race_boat");
    }

    public void startCleanupTask() {
        cleanupTask.cancel();
        cleanupTask = scheduler.runGlobalRepeating(this::cleanupTick, settings.get().cleanupPeriodTicks(), settings.get().cleanupPeriodTicks());
    }

    public void cleanupStaleBoats(Set<UUID> boatIds) {
        for (UUID boatId : boatIds) {
            removeBoat(boatId);
        }
        database.clearOwnedBoats(boatIds).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear stale BoatRace boat records", failure);
            return null;
        });
    }

    public boolean isTrackInUse(String trackId) {
        return sessionByTrack.containsKey(trackId) || trials.values().stream().anyMatch(trial -> trial.trackId().equals(trackId));
    }

    public int trialCount(String trackId) {
        return (int) trials.values().stream().filter(trial -> trial.trackId().equals(trackId)).count();
    }

    public void createRoom(Player player) {
        UUID playerId = player.getUniqueId();
        if (blocked(player)) {
            return;
        }
        if (sessionByPlayer.containsKey(playerId) || pendingJoinTeleports.containsKey(playerId) || trials.containsKey(playerId)) {
            messages.send(player, "already-active");
            return;
        }
        Location location = player.getLocation();
        Optional<Track> found = tracks.atStart(location.getWorld().getUID(), point(location));
        if (found.isEmpty()) {
            messages.send(player, "room-not-at-start");
            return;
        }
        Track track = found.get();
        if (sessionByTrack.containsKey(track.id())) {
            messages.send(player, "room-track-busy");
            return;
        }
        int activeTrials = trialCount(track.id());
        if (activeTrials > 0) {
            messages.send(player, "room-trial-active", Map.of("count", String.valueOf(activeTrials)));
            return;
        }
        String code = codeGenerator.next(sessions::containsKey);
        long now = System.nanoTime();
        RaceSession session = new RaceSession(code, track.id(), playerId, track.slots().size(), System.currentTimeMillis(), now);
        if (!session.join(playerId, player.getName(), now) || sessionByTrack.putIfAbsent(track.id(), code) != null) {
            messages.send(player, "room-track-busy");
            return;
        }
        sessions.put(code, session);
        sessionByPlayer.put(playerId, code);
        plugin.getLogger().info("Race room " + code + " created for track " + track.id());
        messages.send(player, "room-created", Map.of("code", code, "track", track.id()));
    }

    public void joinRoom(Player player, String rawCode) {
        UUID playerId = player.getUniqueId();
        if (blocked(player)) {
            return;
        }
        if (sessionByPlayer.containsKey(playerId) || pendingJoinTeleports.containsKey(playerId) || trials.containsKey(playerId)) {
            messages.send(player, "already-active");
            return;
        }
        String code = rawCode.toUpperCase();
        RaceSession session = sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-found", Map.of("code", code));
            return;
        }
        if (session.phase() != RacePhase.WAITING) {
            messages.send(player, "room-not-waiting");
            return;
        }
        if (session.size() >= session.capacity()) {
            messages.send(player, "room-full");
            return;
        }
        Track track = tracks.get(session.trackId()).orElse(null);
        if (track == null || track.start() == null) {
            messages.send(player, "room-not-waiting");
            return;
        }
        if (pendingJoinTeleports.putIfAbsent(playerId, code) != null) {
            messages.send(player, "already-active");
            return;
        }
        if (sessionByPlayer.containsKey(playerId) || trials.containsKey(playerId)) {
            pendingJoinTeleports.remove(playerId, code);
            messages.send(player, "already-active");
            return;
        }
        long now = System.nanoTime();
        if (!session.join(playerId, player.getName(), now)) {
            pendingJoinTeleports.remove(playerId, code);
            messages.send(player, "room-not-waiting");
            return;
        }
        if (sessionByPlayer.putIfAbsent(playerId, code) != null) {
            session.removeWaiting(playerId, System.nanoTime());
            pendingJoinTeleports.remove(playerId, code);
            messages.send(player, "already-active");
            return;
        }
        World world = Bukkit.getWorld(track.worldId());
        if (world == null) {
            rollbackJoinTeleport(session, playerId, code, true);
            return;
        }
        Point3 center = track.start().center();
        Location destination = new Location(world, center.x(), center.y(), center.z(), player.getYaw(), player.getPitch());
        String playerName = player.getName();
        scheduler.teleport(player, destination).whenComplete((success, failure) -> {
            if (!pendingJoinTeleports.remove(playerId, code)) {
                return;
            }
            if (failure != null || !Boolean.TRUE.equals(success)) {
                rollbackJoinTeleport(session, playerId, code, true);
                return;
            }
            runForPlayer(playerId, owned -> messages.send(owned, "room-joined", Map.of("code", code)));
            broadcast(session, "room-player-joined", Map.of("player", playerName), playerId);
        });
    }

    private void rollbackJoinTeleport(RaceSession session, UUID playerId, String code, boolean notify) {
        pendingJoinTeleports.remove(playerId, code);
        if (session.phase() == RacePhase.WAITING) {
            session.removeWaiting(playerId, System.nanoTime());
        }
        sessionByPlayer.remove(playerId, code);
        if (notify) {
            runForPlayer(playerId, player -> messages.send(player, "room-join-teleport-failed"));
        }
    }

    public void leave(Player player) {
        UUID playerId = player.getUniqueId();
        if (leaveSpectator(player)) {
            return;
        }
        routePenalties.remove(playerId);
        exitMessageTimes.remove(playerId);
        TrialRun trial = trials.remove(playerId);
        if (trial != null) {
            stopUi(playerId);
            messages.send(player, "room-left");
            return;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        RacePhase phase = session.phase();
        if (phase == RacePhase.WAITING) {
            pendingJoinTeleports.remove(playerId, code);
            session.removeWaiting(playerId, System.nanoTime());
            sessionByPlayer.remove(playerId, code);
            messages.send(player, "room-left");
            return;
        }
        if (phase == RacePhase.STAGING || phase == RacePhase.COUNTDOWN) {
            failStaging(code, "有玩家在发车前离开");
            session.removeWaiting(playerId, System.nanoTime());
            sessionByPlayer.remove(playerId, code);
            messages.send(player, "room-left");
            return;
        }
        if (phase == RacePhase.RUNNING || phase == RacePhase.PAUSED) {
            markDnf(session, playerId, true);
            messages.send(player, "room-left");
            finishIfComplete(session);
            return;
        }
        messages.send(player, "room-left");
    }

    public void startByPlayer(Player player) {
        String code = sessionByPlayer.get(player.getUniqueId());
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        if (!session.ownerId().equals(player.getUniqueId()) && !player.hasPermission("boatrace.admin")) {
            messages.send(player, "room-owner-only");
            return;
        }
        startSession(session, player, () -> {
        });
    }

    public void startByAdmin(CommandSender sender, String rawCode) {
        String code = rawCode.toUpperCase();
        RaceSession session = sessions.get(code);
        if (session == null) {
            messages.send(sender, "room-not-found", Map.of("code", code));
            return;
        }
        startSession(session, sender, () -> stopTrialsForRace(session.trackId()));
    }

    public void configureLaps(Player player, int laps) {
        String code = sessionByPlayer.get(player.getUniqueId());
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        if (!session.ownerId().equals(player.getUniqueId()) && !player.hasPermission("boatrace.admin")) {
            messages.send(player, "room-owner-only");
            return;
        }
        configureLaps(session, player, laps);
    }

    public void configureLapsByAdmin(CommandSender sender, String rawCode, int laps) {
        String code = rawCode.toUpperCase();
        RaceSession session = sessions.get(code);
        if (session == null) {
            messages.send(sender, "room-not-found", Map.of("code", code));
            return;
        }
        configureLaps(session, sender, laps);
    }

    private void configureLaps(RaceSession session, CommandSender sender, int laps) {
        if (laps < 1 || laps > MAX_FORMAL_LAPS) {
            messages.send(sender, "invalid-laps", Map.of("max", String.valueOf(MAX_FORMAL_LAPS)));
            return;
        }
        if (session.phase() != RacePhase.WAITING) {
            messages.send(sender, "room-not-waiting");
            return;
        }
        if (!session.configureLaps(laps)) {
            messages.send(sender, "room-not-waiting");
            return;
        }
        messages.send(sender, "room-laps-set", Map.of("code", session.code(), "laps", String.valueOf(laps)));
    }

    private boolean startSession(RaceSession session, CommandSender sender, Runnable beforeStaging) {
        Track track = tracks.get(session.trackId()).orElse(null);
        if (track == null || session.phase() != RacePhase.WAITING || session.size() < 1 || session.size() > track.slots().size()) {
            messages.send(sender, "room-not-waiting");
            return false;
        }
        if (session.laps() < 1) {
            messages.send(sender, "room-laps-required");
            return false;
        }
        List<ParticipantProgress> participants = session.participants().stream()
            .sorted(Comparator.comparingInt(ParticipantProgress::joinOrder))
            .toList();
        if (participants.stream().anyMatch(value -> Bukkit.getPlayer(value.playerId()) == null)) {
            messages.send(sender, "room-owner-offline");
            return false;
        }
        if (participants.stream().anyMatch(value -> pendingJoinTeleports.containsKey(value.playerId()))) {
            messages.send(sender, "room-join-pending");
            return false;
        }
        if (!session.beginStaging(System.nanoTime())) {
            messages.send(sender, "room-not-waiting");
            return false;
        }
        beforeStaging.run();
        session.touch(System.nanoTime());
        stagingFailures.remove(session.code());
        stagingRemaining.put(session.code(), new AtomicInteger(participants.size()));
        for (int index = 0; index < participants.size(); index++) {
            ParticipantProgress participant = participants.get(index);
            stagingAssignments.put(participant.playerId(), track.slots().get(index));
            runForPlayer(participant.playerId(), player -> stageParticipant(session.code(), participant.playerId(), player));
        }
        broadcast(session, "room-starting", Map.of(), null);
        return true;
    }

    private void stageParticipant(String code, UUID playerId, Player player) {
        RaceSession session = sessions.get(code);
        Track track = session == null ? null : tracks.get(session.trackId()).orElse(null);
        StartSlot slot = stagingAssignments.get(playerId);
        if (session == null || track == null || slot == null || session.phase() != RacePhase.STAGING) {
            failStaging(code, "赛道或参赛者状态已变化");
            return;
        }
        World world = Bukkit.getWorld(track.worldId());
        if (world == null) {
            failStaging(code, "赛道世界未加载");
            return;
        }
        stagingOrigins.putIfAbsent(playerId, StoredLocation.from(player.getLocation()));
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Location destination = new Location(world, slot.x(), slot.y(), slot.z(), slot.yaw(), slot.pitch());
        scheduler.teleport(player, destination).whenComplete((success, failure) -> {
            if (failure == null && Boolean.TRUE.equals(success)) {
                stagedPlayers.add(playerId);
            }
            runForPlayer(playerId, owned -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                failStaging(code, "玩家传送失败");
                return;
            }
            RaceSession current = sessions.get(code);
            if (current == null || current.phase() != RacePhase.STAGING) {
                return;
            }
            World targetWorld = Bukkit.getWorld(track.worldId());
            if (targetWorld == null) {
                failStaging(code, "赛道世界未加载");
                return;
            }
            Location stagedLocation = new Location(targetWorld, slot.x(), slot.y(), slot.z(), slot.yaw(), slot.pitch());
            Entity spawned = targetWorld.spawnEntity(stagedLocation, settings.get().raceBoatType());
            if (!(spawned instanceof Boat boat)) {
                spawned.remove();
                failStaging(code, "配置的实体不是船");
                return;
            }
            boat.setInvulnerable(true);
            boat.setPersistent(false);
            boat.getPersistentDataContainer().set(boatKey, PersistentDataType.BYTE, (byte) 1);
            raceBoats.add(boat.getUniqueId());
            boatOwners.put(boat.getUniqueId(), playerId);
            database.registerBoat(boat.getUniqueId()).exceptionally(databaseFailure -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to register BoatRace boat", databaseFailure);
                return null;
            });
            if (!boat.addPassenger(owned)) {
                removeBoatNow(boat);
                failStaging(code, "玩家无法进入比赛船");
                return;
            }
            if (!current.markStaged(playerId, boat.getUniqueId())) {
                removeBoatNow(boat);
                failStaging(code, "参赛者准备状态无效");
                return;
            }
            AtomicInteger remaining = stagingRemaining.get(code);
            if (remaining != null && remaining.decrementAndGet() == 0) {
                beginCountdown(code);
            }
            });
        });
    }

    private void beginCountdown(String code) {
        RaceSession session = sessions.get(code);
        if (session == null || !session.beginCountdown()) {
            failStaging(code, "无法进入倒计时");
            return;
        }
        countdown(code, settings.get().countdownSeconds());
    }

    private void countdown(String code, int seconds) {
        RaceSession session = sessions.get(code);
        if (session == null || session.phase() != RacePhase.COUNTDOWN) {
            return;
        }
        if (seconds <= 0) {
            releaseRace(session);
            return;
        }
        for (ParticipantProgress participant : session.participants()) {
            runForPlayer(participant.playerId(), player -> player.showTitle(Title.title(
                messages.unprefixed("countdown-title", Map.of("seconds", String.valueOf(seconds))),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(850), Duration.ofMillis(150))
            )));
            runForPlayer(participant.playerId(), player -> player.playSound(player.getLocation(), seconds == 1 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, seconds == 1 ? 1.2f : 0.8f));
        }
        scheduler.runGlobalDelayed(() -> countdown(code, seconds - 1), 20L);
    }

    private void releaseRace(RaceSession session) {
        long nowNanos = System.nanoTime();
        long nowEpoch = System.currentTimeMillis();
        if (!session.beginRunning(nowNanos, nowEpoch)) {
            return;
        }
        for (ParticipantProgress participant : session.participants()) {
            runForPlayer(participant.playerId(), player -> {
                stagedPlayers.remove(participant.playerId());
                ParticipantProgress current = session.progress(participant.playerId()).orElse(null);
                if (current == null || current.boatId() == null) {
                    markDnf(session, participant.playerId(), false);
                    return;
                }
                Entity entity = player.getVehicle();
                if (!(entity instanceof Boat boat) || !boat.getUniqueId().equals(current.boatId()) || !Bukkit.isOwnedByCurrentRegion(boat)) {
                    markDnf(session, participant.playerId(), true);
                    return;
                }
                player.showTitle(Title.title(
                    messages.unprefixed("go-title"),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(250))
                ));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                startUi(player);
            });
        }
    }

    private void failStaging(String code, String reason) {
        if (!stagingFailures.add(code)) {
            return;
        }
        RaceSession session = sessions.get(code);
        if (session == null) {
            return;
        }
        List<ParticipantProgress> participants = session.participants();
        session.rollbackStaging(System.nanoTime());
        stagingRemaining.remove(code);
        restoreSpectators(code);
        for (ParticipantProgress participant : participants) {
            stagedPlayers.remove(participant.playerId());
            stagingAssignments.remove(participant.playerId());
            if (participant.boatId() != null) {
                removeBoat(participant.boatId());
            }
            StoredLocation origin = stagingOrigins.remove(participant.playerId());
            runForPlayer(participant.playerId(), player -> {
                if (origin != null) {
                    origin.resolve().ifPresent(location -> scheduler.teleport(player, location));
                }
                messages.send(player, "room-staging-failed", Map.of("reason", reason));
            });
        }
        stagingFailures.remove(code);
    }

    public void cancelByPlayer(Player player) {
        String code = sessionByPlayer.get(player.getUniqueId());
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        if (!session.ownerId().equals(player.getUniqueId()) && !player.hasPermission("boatrace.admin")) {
            messages.send(player, "room-owner-only");
            return;
        }
        if (session.phase() != RacePhase.WAITING) {
            messages.send(player, "room-not-waiting");
            return;
        }
        cancelSession(session, "room-cancelled");
    }

    public void cancelByAdmin(CommandSender sender, String rawCode) {
        String code = rawCode.toUpperCase();
        RaceSession session = sessions.get(code);
        if (session == null) {
            messages.send(sender, "room-not-found", Map.of("code", code));
            return;
        }
        cancelSession(session, "room-cancelled");
        messages.send(sender, "room-cancelled", Map.of("code", code));
    }

    public boolean pause(String rawCode) {
        RaceSession session = sessions.get(rawCode.toUpperCase(java.util.Locale.ROOT));
        if (session == null || !session.pause(System.nanoTime())) {
            return false;
        }
        for (ParticipantProgress participant : session.participants()) {
            stopUi(participant.playerId());
            runForPlayer(participant.playerId(), player -> {
                Entity vehicle = player.getVehicle();
                if (vehicle instanceof Boat boat && participant.boatId() != null
                    && participant.boatId().equals(boat.getUniqueId())) {
                    pausedBoatLocations.put(boat.getUniqueId(), StoredLocation.from(boat.getLocation()));
                    boat.setVelocity(new Vector());
                }
            });
        }
        broadcast(session, "gui-race-paused", Map.of(), null);
        return true;
    }

    public boolean resume(String rawCode) {
        RaceSession session = sessions.get(rawCode.toUpperCase(java.util.Locale.ROOT));
        if (session == null || !session.resume(System.nanoTime())) {
            return false;
        }
        for (ParticipantProgress participant : session.participants()) {
            if (participant.boatId() != null) {
                pausedBoatLocations.remove(participant.boatId());
                pausedCorrections.remove(participant.boatId());
            }
            if (participant.status() == ParticipantStatus.RUNNING) {
                runForPlayer(participant.playerId(), this::startUi);
            }
        }
        broadcast(session, "gui-race-resumed", Map.of(), null);
        return true;
    }

    public boolean end(String rawCode) {
        RaceSession session = sessions.get(rawCode.toUpperCase(java.util.Locale.ROOT));
        if (session == null || session.phase() == RacePhase.WAITING
            || session.phase() == RacePhase.FINISHED || session.phase() == RacePhase.CANCELLED) {
            return false;
        }
        for (ParticipantProgress participant : session.participants()) {
            if (!participant.terminal()) {
                markDnf(session, participant.playerId(), true);
            }
        }
        finishIfComplete(session);
        return true;
    }

    public RaceSession sessionFor(UUID playerId) {
        String code = sessionByPlayer.get(playerId);
        return code == null ? null : sessions.get(code);
    }

    private boolean cancelSession(RaceSession session, String messageKey) {
        if (!session.cancelIfActive()) {
            return false;
        }
        restoreSpectators(session.code());
        List<ParticipantProgress> participants = session.participants();
        removeSessionMappings(session);
        for (ParticipantProgress participant : participants) {
            stagedPlayers.remove(participant.playerId());
            stagingAssignments.remove(participant.playerId());
            stagingOrigins.remove(participant.playerId());
            stopUi(participant.playerId());
            if (participant.boatId() != null) {
                removeBoat(participant.boatId());
            }
            send(participant.playerId(), messageKey, Map.of("code", session.code()));
        }
        return true;
    }

    public void status(Player player) {
        String code = sessionByPlayer.get(player.getUniqueId());
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            messages.send(player, "room-not-member");
            return;
        }
        session.touch(System.nanoTime());
        messages.send(player, "room-status", Map.of(
            "code", session.code(),
            "track", session.trackId(),
            "state", session.phase().name(),
            "laps", session.laps() < 1 ? "未设置" : String.valueOf(session.laps()),
            "players", String.valueOf(session.size()),
            "capacity", String.valueOf(session.capacity())
        ));
        for (ParticipantProgress participant : session.participants()) {
            messages.send(player, "room-participant-name", Map.of("name", participant.playerName()));
        }
    }

    public Optional<CurrentRaceSnapshot> currentRace(UUID playerId) {
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            return Optional.empty();
        }
        Track track = tracks.get(session.trackId()).orElse(null);
        ParticipantProgress current = session.progress(playerId).orElse(null);
        if (track == null || current == null) {
            return Optional.empty();
        }
        List<ParticipantProgress> ordered = orderedParticipants(session, track);
        int rank = 1;
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).playerId().equals(playerId)) {
                rank = index + 1;
                break;
            }
        }
        ParticipantProgress previous = rank > 1 ? ordered.get(rank - 2) : null;
        long elapsed = switch (current.status()) {
            case FINISHED, DNF -> current.finishNanos();
            case RUNNING -> session.elapsedNanos(System.nanoTime());
            case STAGED, WAITING -> 0L;
        };
        int currentLap = switch (current.status()) {
            case FINISHED -> session.laps();
            case DNF -> current.completedLaps();
            case RUNNING -> Math.min(current.completedLaps() + 1, session.laps());
            case STAGED, WAITING -> 0;
        };
        return Optional.of(new CurrentRaceSnapshot(
            session.code(),
            session.trackId(),
            session.phase(),
            current.status(),
            rank,
            session.size(),
            current.completedLaps(),
            currentLap,
            session.laps(),
            elapsed,
            previous == null ? -1L : gapBetween(current, previous),
            previous == null ? null : previous.playerName()
        ));
    }

    public Optional<CurrentTrialSnapshot> currentTrial(UUID playerId) {
        TrialRun trial = trials.get(playerId);
        if (trial == null) {
            return Optional.empty();
        }
        Track track = tracks.get(trial.trackId()).orElse(null);
        if (track == null) {
            return Optional.empty();
        }
        return Optional.of(new CurrentTrialSnapshot(
            trial.trackId(),
            Math.max(0L, System.nanoTime() - trial.lapStartedNanos()),
            trial.nextCheckpoint(),
            track.checkpoints().size()
        ));
    }

    public void rank(Player player) {
        UUID playerId = player.getUniqueId();
        if (sessionByPlayer.containsKey(playerId) || spectators.containsKey(playerId)) {
            messages.send(player, "rank-not-ready");
            return;
        }
        LastRace race = lastRaces.latestForPlayer(playerId).orElse(null);
        if (race == null) {
            messages.send(player, "rank-not-ready");
            return;
        }
        messages.send(player, "race-rank-header", Map.of("code", race.code()));
        for (RaceResultEntry entry : race.entries()) {
            Map<String, String> values = Map.of(
                "player", entry.playerName(),
                "time", TimeFormatter.formatNanos(entry.elapsedNanos()),
                "laps", String.valueOf(entry.completedLaps()),
                "total", String.valueOf(entry.totalLaps())
            );
            if (entry.finished()) {
                messages.send(player, "race-rank-entry", Map.of(
                    "rank", String.valueOf(entry.rank()),
                    "player", entry.playerName(),
                    "time", TimeFormatter.formatNanos(entry.elapsedNanos())
                ));
            } else {
                messages.send(player, "race-rank-unfinished", values);
            }
        }
    }

    public void leaderboard(Player player) {
        RaceSession session = sessionFor(player.getUniqueId());
        if (session == null) {
            messages.send(player, "gui-no-race");
            return;
        }
        Track track = tracks.get(session.trackId()).orElse(null);
        if (track == null) {
            messages.send(player, "gui-no-race");
            return;
        }
        messages.send(player, "race-rank-header", Map.of("code", session.code()));
        List<ParticipantProgress> ordered = orderedParticipants(session, track);
        int rank = 0;
        for (ParticipantProgress participant : ordered) {
            rank++;
            if (participant.status() == ParticipantStatus.FINISHED) {
                messages.send(player, "race-rank-entry", Map.of(
                    "rank", String.valueOf(participant.finishRank()),
                    "player", participant.playerName(),
                    "time", TimeFormatter.formatNanos(participant.finishNanos())));
            } else {
                messages.send(player, "race-rank-unfinished", Map.of(
                    "rank", String.valueOf(rank),
                    "player", participant.playerName(),
                    "laps", String.valueOf(participant.completedLaps()),
                    "total", String.valueOf(session.laps()),
                    "time", TimeFormatter.formatNanos(session.elapsedNanos(System.nanoTime()))));
            }
        }
    }

    public void joinSpectator(Player player, String rawCode) {
        UUID playerId = player.getUniqueId();
        if (blocked(player)) {
            return;
        }
        if (sessionByPlayer.containsKey(playerId) || trials.containsKey(playerId) || spectators.containsKey(playerId)) {
            messages.send(player, "already-active");
            return;
        }
        String code = rawCode.toUpperCase();
        RaceSession session = sessions.get(code);
        if (session == null || session.phase() == RacePhase.WAITING || session.phase() == RacePhase.FINISHED || session.phase() == RacePhase.CANCELLED) {
            messages.send(player, "spectator-race-not-active");
            return;
        }
        Track track = tracks.get(session.trackId()).orElse(null);
        World world = track == null ? null : Bukkit.getWorld(track.worldId());
        if (track == null || world == null || track.start() == null) {
            messages.send(player, "spectator-race-not-active");
            return;
        }
        SpectatorState state = new SpectatorState(
            code,
            player.getGameMode(),
            player.getAllowFlight(),
            player.isFlying(),
            player.isInvisible(),
            player.isInvulnerable(),
            player.isCollidable(),
            player.getCanPickupItems(),
            StoredLocation.from(player.getLocation())
        );
        if (spectators.putIfAbsent(playerId, state) != null) {
            messages.send(player, "already-active");
            return;
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Point3 center = track.start().center();
        Location destination = new Location(world, center.x(), center.y(), center.z(), player.getYaw(), player.getPitch());
        scheduler.teleport(player, destination).whenComplete((success, failure) -> runForPlayer(playerId, owned -> {
            SpectatorState current = spectators.get(playerId);
            if (!state.equals(current)) {
                return;
            }
            if (failure != null || !Boolean.TRUE.equals(success)) {
                spectators.remove(playerId, state);
                spectatorTargets.remove(playerId);
                messages.send(owned, "spectator-teleport-failed");
                return;
            }
            applySpectatorMode(owned);
            startUi(owned);
            messages.send(owned, "spectator-joined", Map.of("code", code));
        }));
    }

    public boolean leaveSpectator(Player player) {
        SpectatorState state = spectators.remove(player.getUniqueId());
        if (state == null) {
            return false;
        }
        restoreSpectator(player.getUniqueId(), state, true);
        return true;
    }

    public void teleportSpectator(Player spectator, String rawTarget) {
        UUID spectatorId = spectator.getUniqueId();
        SpectatorState state = spectators.get(spectatorId);
        if (state == null) {
            messages.send(spectator, "spectator-not-active");
            return;
        }
        RaceSession session = sessions.get(state.code());
        if (session == null || session.phase() == RacePhase.WAITING
            || session.phase() == RacePhase.FINISHED || session.phase() == RacePhase.CANCELLED) {
            messages.send(spectator, "spectator-race-not-active");
            return;
        }
        String targetValue = rawTarget.trim();
        ParticipantProgress target = session.participants().stream()
            .filter(value -> value.playerName().equalsIgnoreCase(targetValue)
                || value.playerId().toString().equalsIgnoreCase(targetValue))
            .findFirst()
            .orElse(null);
        if (target == null) {
            messages.send(spectator, "spectator-target-not-found");
            return;
        }
        Player targetPlayer = Bukkit.getPlayer(target.playerId());
        if (targetPlayer == null) {
            messages.send(spectator, "spectator-target-offline");
            return;
        }
        UUID targetId = target.playerId();
        String targetName = target.playerName();
        scheduler.runEntity(targetPlayer, () -> {
            Location destination = spectatorLocation(targetPlayer.getLocation());
            runForPlayer(spectatorId, viewer -> scheduler.teleport(viewer, destination).whenComplete((success, failure) ->
                runForPlayer(spectatorId, owned -> {
                    if (failure != null || !Boolean.TRUE.equals(success)) {
                        messages.send(owned, "spectator-teleport-failed");
                        return;
                    }
                    applySpectatorMode(owned);
                    spectatorTargets.put(spectatorId, targetId);
                    messages.send(owned, "spectator-target-teleported", Map.of("player", targetName));
                })
            ));
        }, () -> {
            spectatorTargets.remove(spectatorId, targetId);
            send(spectatorId, "spectator-target-offline", Map.of());
        });
    }

    public List<String> spectatorTargetNames(Player spectator) {
        SpectatorState state = spectators.get(spectator.getUniqueId());
        if (state == null) {
            return List.of();
        }
        RaceSession session = sessions.get(state.code());
        if (session == null) {
            return List.of();
        }
        return session.participants().stream().map(ParticipantProgress::playerName).toList();
    }

    private void restoreSpectator(UUID playerId, SpectatorState state, boolean notify) {
        stopUi(playerId);
        spectatorTargets.remove(playerId);
        Location origin = state.previousLocation().resolve().orElse(null);
        if (origin == null) {
            runForPlayer(playerId, player -> {
                restoreSpectatorMode(player, state);
                if (notify) {
                    messages.send(player, "spectator-left");
                }
            });
            return;
        }
        runForPlayer(playerId, player -> scheduler.teleport(player, origin).whenComplete((success, failure) -> runForPlayer(playerId, owned -> {
            restoreSpectatorMode(owned, state);
            if (notify) {
                messages.send(owned, "spectator-left");
            }
        })));
    }

    private static void applySpectatorMode(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
    }

    private static void restoreSpectatorMode(Player player, SpectatorState state) {
        player.setGameMode(state.previousGameMode());
        player.setAllowFlight(state.previousAllowFlight());
        player.setFlying(state.previousFlying() && state.previousAllowFlight());
        player.setInvisible(state.previousInvisible());
        player.setInvulnerable(state.previousInvulnerable());
        player.setCollidable(state.previousCollidable());
        player.setCanPickupItems(state.previousCanPickupItems());
    }

    private static Location spectatorLocation(Location target) {
        Location destination = target.clone().add(0.0, 4.0, 0.0);
        destination.setPitch(Math.max(-45.0f, Math.min(45.0f, target.getPitch())));
        return destination;
    }

    private void restoreSpectators(String code) {
        spectators.entrySet().removeIf(entry -> {
            if (!entry.getValue().code().equals(code)) {
                return false;
            }
            restoreSpectator(entry.getKey(), entry.getValue(), true);
            return true;
        });
    }

    public int stopTrials(String trackId) {
        return stopTrialsInternal(trackId, "trial-cancelled", false);
    }

    public int stopTrialsForRace(String trackId) {
        return stopTrialsInternal(trackId, "trial-forced-by-race", true);
    }

    private int stopTrialsInternal(String trackId, String messageKey, boolean removeBoats) {
        List<UUID> removed = trials.entrySet().stream()
            .filter(entry -> entry.getValue().trackId().equals(trackId))
            .map(Map.Entry::getKey)
            .toList();
        for (UUID playerId : removed) {
            TrialRun trial = trials.remove(playerId);
            if (trial == null) {
                continue;
            }
            routePenalties.remove(playerId);
            exitMessageTimes.remove(playerId);
            stopUi(playerId);
            if (removeBoats) {
                runForPlayer(playerId, player -> {
                    if (player.isInsideVehicle() && player.getVehicle().getUniqueId().equals(trial.boatId())) {
                        player.leaveVehicle();
                    }
                });
                removeBoat(trial.boatId());
            }
            send(playerId, messageKey, Map.of());
        }
        return removed.size();
    }

    public void handleVehicleMove(Boat boat, Player driver, Location fromLocation, Location toLocation) {
        UUID playerId = driver.getUniqueId();
        penalties.rememberName(playerId, driver.getName());
        if (penalties.isBlocked(playerId)) {
            notifyPenaltyBlocked(driver);
            return;
        }
        Point3 from = point(fromLocation);
        Point3 to = point(toLocation);
        String code = sessionByPlayer.get(playerId);
        if (code != null) {
            RaceSession session = sessions.get(code);
            if (session != null && (session.phase() == RacePhase.STAGING || session.phase() == RacePhase.COUNTDOWN)) {
                holdStagedBoat(session, boat, playerId, to);
            } else if (session != null && session.phase() == RacePhase.PAUSED) {
                holdPausedBoat(boat);
            } else if (session != null && session.phase() == RacePhase.RUNNING && !routePenalties.containsKey(playerId)) {
                handleRaceMove(session, boat, driver, from, to);
            }
            return;
        }
        TrialRun trial = trials.get(playerId);
        if (trial != null) {
            if (!routePenalties.containsKey(playerId)) {
                handleTrialMove(trial, boat, driver, from, to);
            }
            return;
        }
        renderIdleStart(driver, to);
        List<Track> crossed = tracks.crossedStarts(toLocation.getWorld().getUID(), from, to).stream()
            .filter(track -> !sessionByTrack.containsKey(track.id()))
            .toList();
        if (crossed.size() == 1) {
            Track track = crossed.getFirst();
            long now = System.nanoTime();
            TrialRun created = new TrialRun(playerId, track.id(), boat.getUniqueId(), 0, now, now + settings.get().trialTimeoutNanos());
            trials.put(playerId, created);
            messages.send(driver, "trial-started");
            startUi(driver);
        }
    }

    private void holdStagedBoat(RaceSession session, Boat boat, UUID playerId, Point3 currentPosition) {
        ParticipantProgress progress = session.progress(playerId).orElse(null);
        StartSlot slot = stagingAssignments.get(playerId);
        if (progress == null || progress.boatId() == null || !progress.boatId().equals(boat.getUniqueId()) || slot == null) {
            return;
        }
        boat.setVelocity(new Vector());
        if (currentPosition.distanceSquared(slot.point()) <= 0.01 || !stagedCorrections.add(boat.getUniqueId())) {
            return;
        }
        UUID boatId = boat.getUniqueId();
        String trackId = session.trackId();
        scheduler.runEntityDelayed(boat, () -> {
            Entity entity = Bukkit.getEntity(boatId);
            Track track = tracks.get(trackId).orElse(null);
            World world = track == null ? null : Bukkit.getWorld(track.worldId());
            if (!(entity instanceof Boat owned) || world == null) {
                stagedCorrections.remove(boatId);
                return;
            }
            owned.setVelocity(new Vector());
            Location destination = new Location(world, slot.x(), slot.y(), slot.z(), slot.yaw(), slot.pitch());
            scheduler.teleport(owned, destination)
                .whenComplete((success, failure) -> stagedCorrections.remove(boatId));
        }, () -> stagedCorrections.remove(boatId), 1L);
    }

    private void holdPausedBoat(Boat boat) {
        boat.setVelocity(new Vector());
        StoredLocation location = pausedBoatLocations.get(boat.getUniqueId());
        if (location == null || !pausedCorrections.add(boat.getUniqueId())) {
            return;
        }
        UUID boatId = boat.getUniqueId();
        location.resolve().ifPresentOrElse(destination -> scheduler.teleport(boat, destination)
            .whenComplete((ignored, failure) -> pausedCorrections.remove(boatId)),
            () -> pausedCorrections.remove(boatId));
    }

    private void beginBacktrackPenalty(Player player, Boat boat, Track track, int restoreCheckpointIndex) {
        if (restoreCheckpointIndex < 0 || restoreCheckpointIndex >= track.checkpoints().size()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        RoutePenalty penalty = new RoutePenalty(playerId, boat.getUniqueId(), track.id(), restoreCheckpointIndex);
        if (routePenalties.putIfAbsent(playerId, penalty) == null) {
            penaltyCountdown(playerId, settings.get().backtrackCountdownSeconds());
        }
    }

    private void penaltyCountdown(UUID playerId, int seconds) {
        RoutePenalty penalty = routePenalties.get(playerId);
        if (penalty == null) {
            return;
        }
        if (seconds <= 0) {
            restorePenalty(penalty);
            return;
        }
        runForPlayer(playerId, player -> player.showTitle(Title.title(
            messages.unprefixed("backtrack-title", Map.of("seconds", String.valueOf(seconds))),
            messages.unprefixed("backtrack-subtitle"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))
        )));
        scheduler.runGlobalDelayed(() -> penaltyCountdown(playerId, seconds - 1), 20L);
    }

    private void restorePenalty(RoutePenalty penalty) {
        scheduler.runGlobal(() -> {
            Entity entity = Bukkit.getEntity(penalty.boatId());
            if (!(entity instanceof Boat boat)) {
                routePenalties.remove(penalty.playerId(), penalty);
                return;
            }
            scheduler.runEntity(boat, () -> {
                if (!penalty.equals(routePenalties.get(penalty.playerId()))) {
                    return;
                }
                Track track = tracks.get(penalty.trackId()).orElse(null);
                World world = track == null ? null : Bukkit.getWorld(track.worldId());
                if (track == null || world == null || penalty.restoreCheckpointIndex() >= track.checkpoints().size()) {
                    routePenalties.remove(penalty.playerId(), penalty);
                    return;
                }
                Entity ownedEntity = Bukkit.getEntity(penalty.boatId());
                if (!(ownedEntity instanceof Boat owned)) {
                    routePenalties.remove(penalty.playerId(), penalty);
                    return;
                }
                Cuboid gate = track.checkpoints().get(penalty.restoreCheckpointIndex());
                Point3 center = gate.center();
                owned.setVelocity(new Vector());
                Location destination = new Location(world, center.x(), gate.minY() + 0.1, center.z(), owned.getYaw(), 0.0f);
                scheduler.teleport(owned, destination)
                    .whenComplete((success, failure) -> scheduler.runGlobalDelayed(() -> {
                        if (routePenalties.remove(penalty.playerId(), penalty) && Boolean.TRUE.equals(success) && failure == null) {
                            send(penalty.playerId(), "backtrack-returned", Map.of());
                        }
                    }, 20L));
            }, () -> routePenalties.remove(penalty.playerId(), penalty));
        });
    }

    private void handleTrialMove(TrialRun trial, Boat boat, Player driver, Point3 from, Point3 to) {
        if (!trial.boatId().equals(boat.getUniqueId())) {
            trials.remove(trial.playerId(), trial);
            stopUi(trial.playerId());
            messages.send(driver, "trial-cancelled");
            return;
        }
        Track track = tracks.get(trial.trackId()).orElse(null);
        if (track == null || sessionByTrack.containsKey(track.id())) {
            trials.remove(trial.playerId(), trial);
            stopUi(trial.playerId());
            messages.send(driver, "trial-cancelled");
            return;
        }
        long now = System.nanoTime();
        if (trial.nextCheckpoint() < track.checkpoints().size()) {
            OptionalInt crossed = ForwardCheckpointDetector.highestCrossed(track, trial.nextCheckpoint(), from, to);
            if (crossed.isPresent()) {
                int recorded = crossed.getAsInt() + 1;
                if (CheckpointAntiCheat.isSuspiciousJump(trial.nextCheckpoint(), recorded)) {
                    handleTrialAntiCheatViolation(trial, driver, boat);
                    return;
                }
                TrialRun advanced = trial.advanceTo(recorded, now + settings.get().trialTimeoutNanos());
                trials.replace(trial.playerId(), trial, advanced);
                playCheckpointSound(driver);
                messages.send(driver, "checkpoint", Map.of(
                    "current", String.valueOf(advanced.nextCheckpoint()),
                    "total", String.valueOf(track.checkpoints().size())
                ));
                return;
            }
            BacktrackDetector.detect(track, trial.nextCheckpoint(), from, to)
                .ifPresent(hit -> beginBacktrackPenalty(driver, boat, track, hit.restoreCheckpointIndex()));
            return;
        }
        if (!track.start().crossed(from, to)) {
            BacktrackDetector.detect(track, trial.nextCheckpoint(), from, to)
                .ifPresent(hit -> beginBacktrackPenalty(driver, boat, track, hit.restoreCheckpointIndex()));
            return;
        }
        long elapsed = Math.max(0L, now - trial.lapStartedNanos());
        trials.replace(trial.playerId(), trial, trial.nextLap(now, now + settings.get().trialTimeoutNanos()));
        playLapSound(driver);
        messages.send(driver, "trial-lap", Map.of("time", TimeFormatter.formatNanos(elapsed)));
        database.recordTrial(track.id(), trial.playerId(), driver.getName(), elapsed, System.currentTimeMillis())
            .whenComplete((result, failure) -> handleTrialSaved(trial.playerId(), track.id(), elapsed, result, failure));
    }

    private void handleTrialSaved(UUID playerId, String trackId, long elapsed, TrialSaveResult result, Throwable failure) {
        if (failure != null) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save BoatRace trial", failure);
            send(playerId, "database-error", Map.of());
            return;
        }
        leaderboards.update(trackId, result.topRecords(), result.recordCount());
        personalStats.invalidate(trackId);
        if (result.personalBest()) {
            send(playerId, "trial-personal-best", Map.of("time", TimeFormatter.formatNanos(elapsed)));
        }
    }

    private void handleRaceMove(RaceSession session, Boat boat, Player driver, Point3 from, Point3 to) {
        ParticipantProgress progress = session.progress(driver.getUniqueId()).orElse(null);
        if (progress == null || progress.status() != ParticipantStatus.RUNNING || !boat.getUniqueId().equals(progress.boatId())) {
            return;
        }
        Track track = tracks.get(session.trackId()).orElse(null);
        if (track == null) {
            markDnf(session, driver.getUniqueId(), true);
            finishIfComplete(session);
            return;
        }
        session.updatePosition(driver.getUniqueId(), to);
        if (progress.nextCheckpoint() < track.checkpoints().size()) {
            OptionalInt crossed = ForwardCheckpointDetector.highestCrossed(track, progress.nextCheckpoint(), from, to);
            if (crossed.isPresent()) {
                int recorded = crossed.getAsInt() + 1;
                if (CheckpointAntiCheat.isSuspiciousJump(progress.nextCheckpoint(), recorded)) {
                    handleAntiCheatViolation(session, driver, boat);
                    return;
                }
                long elapsed = session.elapsedNanos(System.nanoTime());
                session.advanceTo(driver.getUniqueId(), recorded, to, elapsed);
                playCheckpointSound(driver);
                messages.send(driver, "checkpoint", Map.of(
                    "current", String.valueOf(recorded),
                    "total", String.valueOf(track.checkpoints().size())
                ));
                sendRaceGap(driver, session, track);
                return;
            }
            BacktrackDetector.detect(track, progress.nextCheckpoint(), from, to)
                .ifPresent(hit -> beginBacktrackPenalty(driver, boat, track, hit.restoreCheckpointIndex()));
            return;
        }
        if (!track.start().crossed(from, to)) {
            BacktrackDetector.detect(track, progress.nextCheckpoint(), from, to)
                .ifPresent(hit -> beginBacktrackPenalty(driver, boat, track, hit.restoreCheckpointIndex()));
            return;
        }
        int completedLap = progress.completedLaps() + 1;
        if (completedLap < session.laps()) {
            long elapsed = session.elapsedNanos(System.nanoTime());
            if (session.nextLap(driver.getUniqueId(), to, elapsed)) {
                playLapSound(driver);
                messages.send(driver, "race-lap", Map.of(
                    "lap", String.valueOf(completedLap),
                    "total", String.valueOf(session.laps())
                ));
                sendRaceGap(driver, session, track);
            }
            return;
        }
        Optional<ParticipantProgress> finished = session.finish(driver.getUniqueId(), System.nanoTime(), to);
        if (finished.isEmpty()) {
            return;
        }
        playLapSound(driver);
        removeBoatNow(boat);
        stopUi(driver.getUniqueId());
        if (finished.get().finishRank() == 1 && session.claimFirstFinisher()) {
            celebrateFirstFinish(driver.getUniqueId());
        }
        messages.send(driver, "race-finished", Map.of(
            "rank", String.valueOf(finished.get().finishRank()),
            "time", TimeFormatter.formatNanos(finished.get().finishNanos())
        ));
        sendRaceGap(driver, session, track);
        finishIfComplete(session);
    }

    private void celebrateFirstFinish(UUID playerId) {
        runForPlayer(playerId, player -> {
            Firework firework = player.getWorld().spawn(player.getLocation().clone().add(0.0, 1.0, 0.0), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.setPower(1);
            meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.fromRGB(142, 197, 252))
                .withFade(Color.fromRGB(224, 195, 252))
                .trail(true)
                .flicker(true)
                .build());
            firework.setFireworkMeta(meta);
            messages.send(player, "gui-first-finish");
        });
    }

    private void handleAntiCheatViolation(RaceSession session, Player driver, Boat boat) {
        UUID playerId = driver.getUniqueId();
        if (!antiCheatPlayers.add(playerId)) {
            return;
        }
        try {
            ParticipantProgress progress = session.progress(playerId).orElse(null);
            if (progress == null || progress.status() != ParticipantStatus.RUNNING
                || progress.boatId() == null || !progress.boatId().equals(boat.getUniqueId())) {
                return;
            }
            markDnf(session, playerId, true);
            finishIfComplete(session);
            PlayerPenalty penalty = penalties.recordAntiCheat(playerId, driver.getName());
            broadcast(session, "formal-anticheat-announcement", Map.of("player", driver.getName()), null);
            send(playerId, "anticheat-penalty", Map.of(
                "remaining", penalties.remaining(penalty, System.currentTimeMillis())
            ));
        } finally {
            antiCheatPlayers.remove(playerId);
        }
    }

    private void handleTrialAntiCheatViolation(TrialRun trial, Player driver, Boat boat) {
        UUID playerId = trial.playerId();
        if (!antiCheatTrials.add(playerId)) {
            return;
        }
        try {
            if (!trials.remove(playerId, trial)) {
                return;
            }
            routePenalties.remove(playerId);
            exitMessageTimes.remove(playerId);
            stopUi(playerId);
            removeBoatNow(boat);
            PlayerPenalty penalty = penalties.recordAntiCheat(playerId, driver.getName());
            send(playerId, "anticheat-announcement", Map.of());
            send(playerId, "anticheat-penalty", Map.of(
                "remaining", penalties.remaining(penalty, System.currentTimeMillis())
            ));
        } finally {
            antiCheatTrials.remove(playerId);
        }
    }

    private void renderIdleStart(Player player, Point3 position) {
        long now = System.nanoTime();
        long previous = idleParticleTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 250_000_000L) {
            return;
        }
        idleParticleTimes.put(player.getUniqueId(), now);
        tracks.nearestStart(player.getWorld().getUID(), position, settings.get().particleViewDistance())
            .filter(track -> !sessionByTrack.containsKey(track.id()))
            .ifPresent(track -> particles.gate(player, track.start(), Color.LIME));
    }

    public void handleQuit(UUID playerId) {
        idleParticleTimes.remove(playerId);
        penaltyMessageTimes.remove(playerId);
        routePenalties.remove(playerId);
        exitMessageTimes.remove(playerId);
        pendingJoinTeleports.remove(playerId);
        SpectatorState spectator = spectators.remove(playerId);
        if (spectator != null) {
            spectatorTargets.remove(playerId);
            stopUi(playerId);
        }
        TrialRun trial = trials.remove(playerId);
        if (trial != null) {
            stopUi(playerId);
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            return;
        }
        RacePhase phase = session.phase();
        if (phase == RacePhase.WAITING) {
            session.removeWaiting(playerId, System.nanoTime());
            sessionByPlayer.remove(playerId, code);
        } else if (phase == RacePhase.STAGING || phase == RacePhase.COUNTDOWN) {
            failStaging(code, "有玩家在发车前离线");
            session.removeWaiting(playerId, System.nanoTime());
            sessionByPlayer.remove(playerId, code);
        } else if (phase == RacePhase.RUNNING || phase == RacePhase.PAUSED) {
            markDnf(session, playerId, true);
            finishIfComplete(session);
        }
    }

    public void enforcePenalty(UUID playerId) {
        send(playerId, "penalty-admin-ban-notify", Map.of());
        SpectatorState spectator = spectators.remove(playerId);
        if (spectator != null) {
            spectatorTargets.remove(playerId);
            restoreSpectator(playerId, spectator, false);
        }
        TrialRun trial = trials.remove(playerId);
        if (trial != null) {
            routePenalties.remove(playerId);
            exitMessageTimes.remove(playerId);
            stopUi(playerId);
            runForPlayer(playerId, player -> {
                if (player.isInsideVehicle() && player.getVehicle() != null && trial.boatId().equals(player.getVehicle().getUniqueId())) {
                    player.leaveVehicle();
                }
            });
            removeBoat(trial.boatId());
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            return;
        }
        if (session.phase() == RacePhase.WAITING) {
            if (session.ownerId().equals(playerId)) {
                cancelSession(session, "room-cancelled");
            } else {
                session.removeWaiting(playerId, System.nanoTime());
                sessionByPlayer.remove(playerId, code);
                send(playerId, "room-left", Map.of());
            }
            return;
        }
        if (session.phase() == RacePhase.STAGING || session.phase() == RacePhase.COUNTDOWN) {
            cancelSession(session, "room-cancelled");
            return;
        }
        if (session.phase() == RacePhase.RUNNING || session.phase() == RacePhase.PAUSED) {
            markDnf(session, playerId, true);
            finishIfComplete(session);
        }
    }

    public void handleJoin(UUID playerId) {
        handleJoin(playerId, null);
    }

    public void handleJoin(UUID playerId, String playerName) {
        penalties.rememberName(playerId, playerName);
        penalties.get(playerId).filter(value -> value.blocked(System.currentTimeMillis())).ifPresent(value -> {
            send(playerId, value.adminBanned() ? "penalty-admin-blocked" : "penalty-cooldown", Map.of(
                "remaining", penalties.remaining(value, System.currentTimeMillis())
            ));
        });
        long now = System.nanoTime();
        sessions.values().stream()
            .filter(session -> session.phase() == RacePhase.WAITING && session.ownerId().equals(playerId))
            .forEach(session -> session.touch(now));
    }

    public void handleDeath(UUID playerId) {
        routePenalties.remove(playerId);
        exitMessageTimes.remove(playerId);
        TrialRun trial = trials.remove(playerId);
        if (trial != null) {
            stopUi(playerId);
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session != null && (session.phase() == RacePhase.RUNNING || session.phase() == RacePhase.PAUSED)) {
            markDnf(session, playerId, true);
            finishIfComplete(session);
        }
    }

    public void handleVehicleExit(Player player, Entity vehicle) {
        UUID playerId = player.getUniqueId();
        TrialRun trial = trials.get(playerId);
        if (trial != null && trial.boatId().equals(vehicle.getUniqueId())) {
            trials.remove(playerId, trial);
            stopUi(playerId);
            messages.send(player, "trial-cancelled");
            return;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session != null && (session.phase() == RacePhase.RUNNING || session.phase() == RacePhase.PAUSED)) {
            ParticipantProgress progress = session.progress(playerId).orElse(null);
            if (progress != null && progress.status() == ParticipantStatus.RUNNING && vehicle.getUniqueId().equals(progress.boatId())) {
                markDnf(session, playerId, true);
                finishIfComplete(session);
            }
        }
    }

    public boolean shouldBlockVehicleExit(Player player, Entity vehicle) {
        UUID playerId = player.getUniqueId();
        RoutePenalty penalty = routePenalties.get(playerId);
        if (penalty != null && penalty.boatId().equals(vehicle.getUniqueId())) {
            return true;
        }
        TrialRun trial = trials.get(playerId);
        if (trial != null && trial.boatId().equals(vehicle.getUniqueId())) {
            return true;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        ParticipantProgress progress = session == null ? null : session.progress(playerId).orElse(null);
        if (session == null || progress == null || progress.boatId() == null || !progress.boatId().equals(vehicle.getUniqueId())) {
            return false;
        }
        RacePhase phase = session.phase();
        return phase == RacePhase.STAGING || phase == RacePhase.COUNTDOWN
            || (phase == RacePhase.RUNNING || phase == RacePhase.PAUSED) && progress.status() == ParticipantStatus.RUNNING;
    }

    public void notifyExitBlocked(Player player) {
        long now = System.nanoTime();
        long previous = exitMessageTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous >= 1_000_000_000L) {
            exitMessageTimes.put(player.getUniqueId(), now);
            messages.send(player, "exit-command-only");
        }
    }

    public boolean shouldCancelRaceBoatCollision(UUID boatId, Entity other) {
        if (!raceBoats.contains(boatId)) {
            return false;
        }
        return raceBoats.contains(other.getUniqueId())
            || other instanceof Player player && (isProtectedPlayer(player.getUniqueId()) || isSpectator(player.getUniqueId()));
    }

    public boolean isProtectedPlayer(UUID playerId) {
        if (isSpectator(playerId)) {
            return true;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        if (session == null) {
            return false;
        }
        RacePhase phase = session.phase();
        if (phase == RacePhase.STAGING || phase == RacePhase.COUNTDOWN) {
            return true;
        }
        ParticipantProgress progress = session.progress(playerId).orElse(null);
        return (phase == RacePhase.RUNNING || phase == RacePhase.PAUSED)
            && progress != null && progress.status() == ParticipantStatus.RUNNING;
    }

    public boolean isRaceBoat(UUID entityId) {
        return raceBoats.contains(entityId);
    }

    public boolean isSpectator(UUID playerId) {
        return spectators.containsKey(playerId);
    }

    public boolean shouldBlockSpectatorMove(UUID playerId, Location destination) {
        if (!isSpectator(playerId) || destination == null || destination.getWorld() == null) {
            return false;
        }
        SpectatorState state = spectators.get(playerId);
        RaceSession session = state == null ? null : sessions.get(state.code());
        if (session == null) {
            return false;
        }
        Track track = tracks.get(session.trackId()).orElse(null);
        if (track == null || !track.worldId().equals(destination.getWorld().getUID())) {
            return false;
        }
        UUID worldId = destination.getWorld().getUID();
        Point3 point = point(destination);
        for (ParticipantProgress participant : session.participants()) {
            if (participant.position() == null || participant.status() == ParticipantStatus.DNF
                || !worldId.equals(track.worldId())) {
                continue;
            }
            if (point.distanceSquared(participant.position()) < 9.0D) {
                return true;
            }
        }
        return false;
    }

    public boolean isStaged(UUID playerId) {
        return stagedPlayers.contains(playerId);
    }

    private void markDnf(RaceSession session, UUID playerId, boolean removeBoat) {
        routePenalties.remove(playerId);
        exitMessageTimes.remove(playerId);
        ParticipantProgress progress = session.dnf(playerId, System.nanoTime()).orElse(null);
        stagedPlayers.remove(playerId);
        stopUi(playerId);
        if (progress != null && removeBoat && progress.boatId() != null) {
            removeBoat(progress.boatId());
        }
        send(playerId, "race-dnf", Map.of());
    }

    private void finishIfComplete(RaceSession session) {
        if (session.allTerminal()) {
            finishSession(session);
        }
    }

    private void finishSession(RaceSession session) {
        if (!endingSessions.add(session.code())) {
            return;
        }
        session.finishPhase();
        long endedAt = System.currentTimeMillis();
        LastRace race = new LastRace(
            session.trackId(),
            session.code(),
            session.goEpochMillis() == 0L ? session.createdEpochMillis() : session.goEpochMillis(),
            endedAt,
            session.resultEntries()
        );
        lastRaces.update(race);
        RaceResultEntry winner = race.entries().stream()
            .filter(RaceResultEntry::finished)
            .findFirst()
            .orElse(null);
        if (winner == null) {
            scheduler.runGlobal(() -> Bukkit.broadcast(messages.unprefixed("gui-race-no-winner-broadcast")));
        } else {
            scheduler.runGlobal(() -> Bukkit.broadcast(messages.unprefixed("gui-race-winner-broadcast", Map.of(
                "player", winner.playerName(),
                "time", TimeFormatter.formatNanos(winner.elapsedNanos())
            ))));
        }
        database.saveLastRace(race).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to save BoatRace result", failure);
            return null;
        });
        List<ParticipantProgress> participants = session.participants();
        restoreSpectators(session.code());
        removeSessionMappings(session);
        for (ParticipantProgress participant : participants) {
            stopUi(participant.playerId());
            if (participant.boatId() != null) {
                removeBoat(participant.boatId());
            }
            sendResults(participant.playerId(), race);
        }
        endingSessions.remove(session.code());
    }

    private void sendResults(UUID playerId, LastRace race) {
        runForPlayer(playerId, player -> {
            messages.send(player, "race-results-header", Map.of("code", race.code()));
            for (RaceResultEntry entry : race.entries()) {
                if (entry.finished()) {
                    messages.send(player, "race-results-entry", Map.of(
                        "rank", String.valueOf(entry.rank()),
                        "player", entry.playerName(),
                        "time", TimeFormatter.formatNanos(entry.elapsedNanos())
                    ));
                } else {
                    messages.send(player, "race-results-dnf", Map.of(
                        "player", entry.playerName(),
                        "laps", String.valueOf(entry.completedLaps()),
                        "total", String.valueOf(entry.totalLaps()),
                        "time", TimeFormatter.formatNanos(entry.elapsedNanos())
                    ));
                }
            }
        });
    }

    private void removeSessionMappings(RaceSession session) {
        sessions.remove(session.code(), session);
        sessionByTrack.remove(session.trackId(), session.code());
        for (ParticipantProgress participant : session.participants()) {
            sessionByPlayer.remove(participant.playerId(), session.code());
            pendingJoinTeleports.remove(participant.playerId(), session.code());
            routePenalties.remove(participant.playerId());
            exitMessageTimes.remove(participant.playerId());
            if (participant.boatId() != null) {
                pausedBoatLocations.remove(participant.boatId());
                pausedCorrections.remove(participant.boatId());
            }
            stagingOrigins.remove(participant.playerId());
            stagingAssignments.remove(participant.playerId());
            stagedPlayers.remove(participant.playerId());
        }
        stagingRemaining.remove(session.code());
        stagingFailures.remove(session.code());
    }

    private void cleanupTick() {
        long now = System.nanoTime();
        for (Map.Entry<UUID, TrialRun> entry : trials.entrySet()) {
            if (now >= entry.getValue().expiresAtNanos() && trials.remove(entry.getKey(), entry.getValue())) {
                routePenalties.remove(entry.getKey());
                exitMessageTimes.remove(entry.getKey());
                stopUi(entry.getKey());
                send(entry.getKey(), "trial-timeout", Map.of());
            }
        }
        for (RaceSession session : List.copyOf(sessions.values())) {
            if (session.phase() == RacePhase.WAITING && now - session.lastActivityNanos() >= settings.get().lobbyIdleTimeoutNanos()) {
                cancelSession(session, "room-expired");
            } else if (session.phase() == RacePhase.RUNNING && session.elapsedNanos(now) >= settings.get().raceTimeoutNanos()) {
                for (ParticipantProgress participant : session.participants()) {
                    if (!participant.terminal()) {
                        markDnf(session, participant.playerId(), true);
                    }
                }
                finishIfComplete(session);
            }
        }
    }

    private void startUi(Player player) {
        UUID playerId = player.getUniqueId();
        TaskHandle task = scheduler.runEntityRepeating(
            player,
            () -> renderUi(playerId),
            () -> stopUi(playerId),
            1L,
            settings.get().particlePeriodTicks()
        );
        TaskHandle previous = uiTasks.put(playerId, task);
        if (previous != null) {
            previous.cancel();
        }
    }

    private void renderUi(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            stopUi(playerId);
            return;
        }
        TrialRun trial = trials.get(playerId);
        if (trial != null) {
            Track track = tracks.get(trial.trackId()).orElse(null);
            if (track == null || !track.worldId().equals(player.getWorld().getUID())) {
                return;
            }
            Cuboid target = track.target(trial.nextCheckpoint());
            particles.gate(player, target, trial.nextCheckpoint() < track.checkpoints().size() ? Color.AQUA : Color.LIME);
            long elapsed = Math.max(0L, System.nanoTime() - trial.lapStartedNanos());
            player.sendActionBar(Component.text("自由计时 " + TimeFormatter.formatNanos(elapsed) + " | 记录点 " + Math.min(trial.nextCheckpoint() + 1, track.checkpoints().size()) + "/" + track.checkpoints().size(), NamedTextColor.AQUA));
            return;
        }
        String code = sessionByPlayer.get(playerId);
        RaceSession session = code == null ? null : sessions.get(code);
        SpectatorState spectator = spectators.get(playerId);
        if (spectator != null) {
            session = sessions.get(spectator.code());
            if (session == null || (session.phase() != RacePhase.STAGING
                && session.phase() != RacePhase.COUNTDOWN
                && session.phase() != RacePhase.RUNNING
                && session.phase() != RacePhase.PAUSED)) {
                stopUi(playerId);
                return;
            }
            UUID targetId = spectatorTargets.get(playerId);
            if (targetId != null) {
                Player targetPlayer = Bukkit.getPlayer(targetId);
                if (targetPlayer == null) {
                    spectatorTargets.remove(playerId, targetId);
                } else {
                    followSpectator(playerId, targetId, targetPlayer);
                }
            }
            renderSpectatorUi(player, session);
            return;
        }
        if (session == null || (session.phase() != RacePhase.RUNNING && session.phase() != RacePhase.PAUSED)) {
            stopUi(playerId);
            return;
        }
        ParticipantProgress progress = session.progress(playerId).orElse(null);
        Track track = tracks.get(session.trackId()).orElse(null);
        if (progress == null || track == null || progress.status() != ParticipantStatus.RUNNING || !track.worldId().equals(player.getWorld().getUID())) {
            return;
        }
        String leader = session.leaderName();
        if (leader == null) {
            player.sendActionBar(messages.unprefixed("gui-leaderboard-empty-actionbar"));
        } else {
            player.sendActionBar(messages.unprefixed("gui-leaderboard-actionbar", Map.of(
                "player", leader, "time", TimeFormatter.formatNanos(session.leaderTimeNanos()))));
        }
        Cuboid target = track.target(progress.nextCheckpoint());
        particles.gate(player, target, progress.nextCheckpoint() < track.checkpoints().size() ? Color.AQUA : Color.LIME);
        long elapsed = session.elapsedNanos(System.nanoTime());
        int rank = liveRank(session, track, playerId);
        ParticipantProgress ahead = previousParticipant(session, track, progress);
        long gap = ahead == null ? 0L : gapBetween(progress, ahead);
        NamedTextColor baseColor = ahead == null ? NamedTextColor.GREEN : NamedTextColor.WHITE;
        Component actionBar = Component.text(
            "你 " + player.getName()
                + " | 排名 " + rank + "/" + session.size()
                + " | 圈数 " + Math.min(progress.completedLaps() + 1, session.laps()) + "/" + session.laps()
                + " | 用时 " + TimeFormatter.formatNanos(elapsed)
                + " | 记录点 " + Math.min(progress.nextCheckpoint() + 1, track.checkpoints().size()) + "/" + track.checkpoints().size(),
            baseColor
        );
        if (ahead != null) {
            actionBar = actionBar.append(Component.text(" | 前方 " + ahead.playerName() + " +" + TimeFormatter.formatNanos(gap), NamedTextColor.RED));
        }
        player.sendActionBar(actionBar);
    }

    private void followSpectator(UUID spectatorId, UUID targetId, Player targetPlayer) {
        scheduler.runEntity(targetPlayer, () -> {
            Location destination = spectatorLocation(targetPlayer.getLocation());
            runForPlayer(spectatorId, viewer -> {
                if (!targetId.equals(spectatorTargets.get(spectatorId)) || !spectators.containsKey(spectatorId)) {
                    return;
                }
                applySpectatorMode(viewer);
                scheduler.teleport(viewer, destination);
            });
        }, () -> spectatorTargets.remove(spectatorId, targetId));
    }

    private void renderSpectatorUi(Player player, RaceSession session) {
        Track track = tracks.get(session.trackId()).orElse(null);
        if (track == null) {
            return;
        }
        List<ParticipantProgress> values = orderedParticipants(session, track);
        if (values.isEmpty()) {
            player.sendActionBar(Component.text("观战 | 比赛准备中", NamedTextColor.AQUA));
            return;
        }
        Component actionBar = Component.text("观战 | ", NamedTextColor.AQUA);
        for (int index = 0; index < values.size(); index++) {
            ParticipantProgress participant = values.get(index);
            if (index > 0) {
                actionBar = actionBar.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            }
            NamedTextColor color = switch (participant.status()) {
                case FINISHED -> NamedTextColor.GREEN;
                case DNF -> NamedTextColor.RED;
                default -> NamedTextColor.WHITE;
            };
            actionBar = actionBar.append(Component.text(
                "P" + (index + 1) + " " + participant.playerName() + " " + spectatorProgress(participant, session),
                color
            ));
        }
        player.sendActionBar(actionBar);
    }

    private long gapToPrevious(RaceSession session, Track track, ParticipantProgress current) {
        ParticipantProgress previous = previousParticipant(session, track, current);
        return previous == null ? -1L : gapBetween(current, previous);
    }

    private void sendRaceGap(Player player, RaceSession session, Track track) {
        ParticipantProgress current = session.progress(player.getUniqueId()).orElse(null);
        if (current == null) {
            return;
        }
        ParticipantProgress previous = previousParticipant(session, track, current);
        if (previous == null) {
            return;
        }
        messages.send(player, "race-gap-name", Map.of(
            "player", previous.playerName(),
            "time", TimeFormatter.formatNanos(gapBetween(current, previous))
        ));
    }

    private ParticipantProgress previousParticipant(RaceSession session, Track track, ParticipantProgress current) {
        List<ParticipantProgress> values = orderedParticipants(session, track);
        int index = values.indexOf(current);
        return index > 0 ? values.get(index - 1) : null;
    }

    private static long gapBetween(ParticipantProgress current, ParticipantProgress previous) {
        long currentTime = current.progressNanos();
        long previousTime = previous.progressNanos();
        if (currentTime <= 0L || previousTime <= 0L) {
            return 0L;
        }
        return Math.abs(currentTime - previousTime);
    }

    private List<ParticipantProgress> orderedParticipants(RaceSession session, Track track) {
        List<ParticipantProgress> values = new ArrayList<>(session.participants());
        values.sort(Comparator
            .comparingInt((ParticipantProgress value) -> statusOrder(value.status()))
            .thenComparingInt(value -> value.status() == ParticipantStatus.FINISHED ? value.finishRank() : -value.completedLaps())
            .thenComparingInt(value -> value.status() == ParticipantStatus.FINISHED ? 0 : -value.nextCheckpoint())
            .thenComparingDouble(value -> distanceToTarget(value, track))
            .thenComparingInt(ParticipantProgress::joinOrder));
        return values;
    }

    private static String spectatorProgress(ParticipantProgress participant, RaceSession session) {
        return switch (participant.status()) {
            case FINISHED -> "完成 " + session.laps() + "/" + session.laps();
            case DNF -> "未完成 " + participant.completedLaps() + "/" + session.laps();
            case RUNNING -> "圈数 " + Math.min(participant.completedLaps() + 1, session.laps()) + "/" + session.laps();
            case STAGED, WAITING -> "准备";
        };
    }

    private void playCheckpointSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1.0f, 1.6f);
    }

    private void playLapSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private int liveRank(RaceSession session, Track track, UUID playerId) {
        List<ParticipantProgress> values = orderedParticipants(session, track);
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).playerId().equals(playerId)) {
                return index + 1;
            }
        }
        return values.size();
    }

    private static int statusOrder(ParticipantStatus status) {
        return switch (status) {
            case FINISHED -> 0;
            case RUNNING -> 1;
            case STAGED, WAITING -> 2;
            case DNF -> 3;
        };
    }

    private static double distanceToTarget(ParticipantProgress progress, Track track) {
        if (progress.position() == null || progress.status() != ParticipantStatus.RUNNING) {
            return Double.MAX_VALUE;
        }
        return progress.position().distanceSquared(track.target(progress.nextCheckpoint()).center());
    }

    private void stopUi(UUID playerId) {
        TaskHandle task = uiTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void removeBoatNow(Boat boat) {
        UUID boatId = boat.getUniqueId();
        raceBoats.remove(boatId);
        stagedCorrections.remove(boatId);
        pausedBoatLocations.remove(boatId);
        pausedCorrections.remove(boatId);
        routePenalties.entrySet().removeIf(entry -> entry.getValue().boatId().equals(boatId));
        boatOwners.remove(boatId);
        database.unregisterBoat(boatId).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to unregister BoatRace boat", failure);
            return null;
        });
        boat.eject();
        boat.remove();
    }

    private void removeBoat(UUID boatId) {
        raceBoats.remove(boatId);
        stagedCorrections.remove(boatId);
        pausedBoatLocations.remove(boatId);
        pausedCorrections.remove(boatId);
        routePenalties.entrySet().removeIf(entry -> entry.getValue().boatId().equals(boatId));
        boatOwners.remove(boatId);
        database.unregisterBoat(boatId).exceptionally(failure -> null);
        scheduler.runGlobal(() -> {
            Entity entity = Bukkit.getEntity(boatId);
            if (entity == null) {
                return;
            }
            scheduler.runEntity(entity, () -> {
                Entity owned = Bukkit.getEntity(boatId);
                if (owned != null) {
                    owned.eject();
                    owned.remove();
                }
            }, null);
        });
    }

    private void broadcast(RaceSession session, String key, Map<String, String> values, UUID excluded) {
        for (ParticipantProgress participant : session.participants()) {
            if (excluded == null || !excluded.equals(participant.playerId())) {
                send(participant.playerId(), key, values);
            }
        }
    }

    private void send(UUID playerId, String key, Map<String, String> values) {
        runForPlayer(playerId, player -> messages.send(player, key, values));
    }

    private boolean blocked(Player player) {
        UUID playerId = player.getUniqueId();
        penalties.rememberName(playerId, player.getName());
        Optional<PlayerPenalty> active = penalties.active(playerId);
        if (active.isEmpty()) {
            return false;
        }
        PlayerPenalty penalty = active.get();
        messages.send(player, penalty.adminBanned() ? "penalty-admin-blocked" : "penalty-cooldown", Map.of(
            "remaining", penalties.remaining(penalty, System.currentTimeMillis())
        ));
        return true;
    }

    private void notifyPenaltyBlocked(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.nanoTime();
        long previous = penaltyMessageTimes.getOrDefault(playerId, 0L);
        if (now - previous < 1_000_000_000L) {
            return;
        }
        penaltyMessageTimes.put(playerId, now);
        blocked(player);
    }

    private void runForPlayer(UUID playerId, Consumer<Player> action) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return;
            }
            scheduler.runEntity(player, () -> {
                Player owned = Bukkit.getPlayer(playerId);
                if (owned != null) {
                    action.accept(owned);
                }
            }, null);
        });
    }

    private static Point3 point(Location location) {
        return new Point3(location.getX(), location.getY(), location.getZ());
    }

    public Map<String, RaceSession> sessionSnapshot() {
        return Map.copyOf(sessions);
    }

    public void shutdown() {
        cleanupTask.cancel();
        uiTasks.values().forEach(TaskHandle::cancel);
        uiTasks.clear();
        raceBoats.clear();
        boatOwners.clear();
        sessions.clear();
        sessionByTrack.clear();
        sessionByPlayer.clear();
        pendingJoinTeleports.clear();
        trials.clear();
        stagingOrigins.clear();
        stagingAssignments.clear();
        stagingRemaining.clear();
        stagingFailures.clear();
        stagedPlayers.clear();
        stagedCorrections.clear();
        pausedBoatLocations.clear();
        pausedCorrections.clear();
        routePenalties.clear();
        exitMessageTimes.clear();
        penaltyMessageTimes.clear();
        idleParticleTimes.clear();
        spectators.clear();
        spectatorTargets.clear();
        endingSessions.clear();
        antiCheatPlayers.clear();
        antiCheatTrials.clear();
    }

    public record CurrentRaceSnapshot(
        String code,
        String trackId,
        RacePhase phase,
        ParticipantStatus status,
        int rank,
        int participants,
        int completedLaps,
        int currentLap,
        int totalLaps,
        long elapsedNanos,
        long gapNanos,
        String aheadName
    ) {
    }

    public record CurrentTrialSnapshot(
        String trackId,
        long elapsedNanos,
        int nextCheckpoint,
        int totalCheckpoints
    ) {
    }

    private record SpectatorState(
        String code,
        GameMode previousGameMode,
        boolean previousAllowFlight,
        boolean previousFlying,
        boolean previousInvisible,
        boolean previousInvulnerable,
        boolean previousCollidable,
        boolean previousCanPickupItems,
        StoredLocation previousLocation
    ) {
    }
}
