package cn.cloudfl4re.boatrace.papi;

import cn.cloudfl4re.boatrace.config.PluginSettings;
import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.ParticipantStatus;
import cn.cloudfl4re.boatrace.model.PersonalTrialStat;
import cn.cloudfl4re.boatrace.model.PlayerPenalty;
import cn.cloudfl4re.boatrace.model.RacePhase;
import cn.cloudfl4re.boatrace.model.RaceResultEntry;
import cn.cloudfl4re.boatrace.model.TrialRecord;
import cn.cloudfl4re.boatrace.service.LastRaceService;
import cn.cloudfl4re.boatrace.service.LeaderboardService;
import cn.cloudfl4re.boatrace.service.PenaltyService;
import cn.cloudfl4re.boatrace.service.PersonalStatsService;
import cn.cloudfl4re.boatrace.service.RaceManager;
import cn.cloudfl4re.boatrace.util.TimeFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class BoatRaceExpansion extends PlaceholderExpansion {
    private static final String EMPTY_STATUS = "暂无比赛";
    private static final String EMPTY_LAPS = "0/0";
    private static final String EMPTY_TRACK = "无";
    private static final String EMPTY_RANK = "暂无排名";
    private static final String NO_COOLDOWN = "无";

    private final Plugin plugin;
    private final LeaderboardService leaderboards;
    private final LastRaceService lastRaces;
    private final RaceManager races;
    private final PersonalStatsService personalStats;
    private final PenaltyService penalties;
    private final Supplier<PluginSettings> settings;

    public BoatRaceExpansion(
        Plugin plugin,
        LeaderboardService leaderboards,
        LastRaceService lastRaces,
        RaceManager races,
        PersonalStatsService personalStats,
        PenaltyService penalties,
        Supplier<PluginSettings> settings
    ) {
        this.plugin = plugin;
        this.leaderboards = leaderboards;
        this.lastRaces = lastRaces;
        this.races = races;
        this.personalStats = personalStats;
        this.penalties = penalties;
        this.settings = settings;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "boatrace";
    }

    @Override
    public @NotNull String getAuthor() {
        return "cloudfl4re";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public @Nullable String getPlugin() {
        return plugin.getName();
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        Optional<PapiRequest> parsed = PapiParser.parse(params.trim());
        if (parsed.isEmpty()) {
            return null;
        }
        PapiRequest request = parsed.get();
        return switch (request.kind()) {
            case TRIAL_LEADERBOARD -> trialLeaderboard(request);
            case TRIAL_RECORD_COUNT -> String.valueOf(leaderboards.recordCount(request.trackId()));
            case FORMAL_LEADERBOARD -> formalLeaderboard(request);
            case FORMAL_RECORD_COUNT -> lastRaces.get(request.trackId()).map(race -> String.valueOf(race.entries().size())).orElse("0");
            case CURRENT_RACE -> currentRace(player, request.field());
            case CURRENT_TRIAL -> currentTrial(player, request.field());
            case PERSONAL_TRIAL -> personalTrial(player, request);
            case PERSONAL_LAST_RACE -> personalLastRace(player, request.field());
            case PERSONAL_INFO -> personalInfo(player, request.field());
        };
    }

    private String trialLeaderboard(PapiRequest request) {
        PluginSettings current = settings.get();
        Optional<TrialRecord> record = leaderboards.at(request.trackId(), request.rank());
        if (record.isEmpty()) {
            return emptyLeaderboard(request.rank(), request.field(), current);
        }
        TrialRecord value = record.get();
        String time = TimeFormatter.formatNanos(value.bestNanos());
        return switch (request.field()) {
            case NAME -> value.playerName();
            case TIME -> time;
            case LINE -> request.rank() + ". " + value.playerName() + " - " + time;
            default -> emptyLeaderboard(request.rank(), request.field(), current);
        };
    }

    private String formalLeaderboard(PapiRequest request) {
        PluginSettings current = settings.get();
        Optional<LastRace> race = lastRaces.get(request.trackId());
        if (race.isEmpty() || request.rank() > race.get().entries().size()) {
            return emptyLeaderboard(request.rank(), request.field(), current);
        }
        RaceResultEntry entry = race.get().entries().get(request.rank() - 1);
        String time = TimeFormatter.formatNanos(entry.elapsedNanos());
        String laps = entry.completedLaps() + "/" + entry.totalLaps();
        String status = entry.finished() ? "已完成" : "未完成";
        return switch (request.field()) {
            case NAME -> entry.playerName();
            case TIME -> time;
            case LAPS -> laps;
            case STATUS -> status;
            case LINE -> entry.finished()
                ? request.rank() + ". " + entry.playerName() + " - " + time
                : request.rank() + ". " + entry.playerName() + " - 未完成 " + laps + " 圈 - " + time;
            default -> emptyLeaderboard(request.rank(), request.field(), current);
        };
    }

    private String currentRace(OfflinePlayer player, PapiRequest.Field field) {
        PluginSettings current = settings.get();
        if (player == null) {
            return emptyCurrentRace(field, current);
        }
        Optional<RaceManager.CurrentRaceSnapshot> snapshot = races.currentRace(player.getUniqueId());
        if (snapshot.isEmpty()) {
            return emptyCurrentRace(field, current);
        }
        RaceManager.CurrentRaceSnapshot value = snapshot.get();
        return switch (field) {
            case RANK -> value.rank() + "/" + value.participants();
            case LAPS -> value.currentLap() + "/" + value.totalLaps();
            case TIME -> TimeFormatter.formatNanos(value.elapsedNanos());
            case GAP -> value.gapNanos() < 0L ? current.papiEmptyTime() : TimeFormatter.formatNanos(value.gapNanos());
            case AHEAD -> value.aheadName() == null ? current.papiEmptyName() : value.aheadName();
            case TRACK -> value.trackId();
            case STATUS -> raceStatus(value.status());
            default -> emptyCurrentRace(field, current);
        };
    }

    private String currentTrial(OfflinePlayer player, PapiRequest.Field field) {
        PluginSettings current = settings.get();
        if (player == null) {
            return emptyCurrentTrial(field, current);
        }
        Optional<RaceManager.CurrentTrialSnapshot> snapshot = races.currentTrial(player.getUniqueId());
        if (snapshot.isEmpty()) {
            return emptyCurrentTrial(field, current);
        }
        RaceManager.CurrentTrialSnapshot value = snapshot.get();
        return switch (field) {
            case TIME -> TimeFormatter.formatNanos(value.elapsedNanos());
            case STATUS -> "计时中";
            case TRACK -> value.trackId();
            case CHECKPOINT -> Math.min(value.nextCheckpoint() + 1, value.totalCheckpoints()) + "/" + value.totalCheckpoints();
            default -> emptyCurrentTrial(field, current);
        };
    }

    private String personalTrial(OfflinePlayer player, PapiRequest request) {
        PluginSettings current = settings.get();
        if (request.field() == PapiRequest.Field.COUNT) {
            return String.valueOf(leaderboards.recordCount(request.trackId()));
        }
        if (player == null) {
            return emptyPersonalTrial(request.field(), current);
        }
        Optional<PersonalTrialStat> stat = personalStats.trial(request.trackId(), player.getUniqueId());
        if (stat.isEmpty()) {
            return emptyPersonalTrial(request.field(), current);
        }
        PersonalTrialStat value = stat.get();
        String time = TimeFormatter.formatNanos(value.bestNanos());
        return switch (request.field()) {
            case TIME -> time;
            case RANK -> value.rank() + "/" + value.totalRecords();
            case NAME -> value.playerName();
            case TRACK -> value.trackId();
            case COUNT -> String.valueOf(value.totalRecords());
            case LINE -> value.rank() + ". " + value.playerName() + " - " + time;
            default -> emptyPersonalTrial(request.field(), current);
        };
    }

    private String personalLastRace(OfflinePlayer player, PapiRequest.Field field) {
        PluginSettings current = settings.get();
        if (player == null) {
            return emptyPersonalLastRace(field, current);
        }
        UUID playerId = player.getUniqueId();
        LastRace race = lastRaces.latestForPlayer(playerId).orElse(null);
        RaceResultEntry entry = race == null ? null : race.entries().stream()
            .filter(candidate -> candidate.playerId().equals(playerId))
            .findFirst()
            .orElse(null);
        if (entry == null) {
            return emptyPersonalLastRace(field, current);
        }
        String time = TimeFormatter.formatNanos(entry.elapsedNanos());
        String laps = entry.completedLaps() + "/" + entry.totalLaps();
        return switch (field) {
            case RANK -> entry.finished() ? String.valueOf(entry.rank()) : "未完成";
            case TIME -> time;
            case LAPS -> laps;
            case TRACK -> race.trackId();
            case NAME -> entry.playerName();
            case STATUS -> entry.finished() ? "已完成" : "未完成";
            case LINE -> entry.finished()
                ? entry.rank() + ". " + race.trackId() + " - " + time
                : race.trackId() + " - 未完成 " + laps + " 圈 - " + time;
            default -> emptyPersonalLastRace(field, current);
        };
    }

    private String personalInfo(OfflinePlayer player, PapiRequest.Field field) {
        PluginSettings current = settings.get();
        if (player == null) {
            return emptyPersonalInfo(field, current);
        }
        UUID playerId = player.getUniqueId();
        PlayerPenalty penalty = penalties.get(playerId).orElse(null);
        long now = System.currentTimeMillis();
        return switch (field) {
            case VIOLATIONS -> String.valueOf(penalty == null ? 0 : penalty.violationCount());
            case COOLDOWN -> penalty == null || !penalty.blocked(now) ? NO_COOLDOWN : penalties.remaining(penalty, now);
            case STATUS -> personalStatus(playerId, penalty, now);
            default -> emptyPersonalInfo(field, current);
        };
    }

    private String personalStatus(UUID playerId, PlayerPenalty penalty, long nowEpochMillis) {
        if (penalty != null && penalty.adminBanned()) {
            return "已封禁";
        }
        Optional<RaceManager.CurrentRaceSnapshot> race = races.currentRace(playerId);
        if (race.isPresent()) {
            return raceStatus(race.get());
        }
        if (races.currentTrial(playerId).isPresent()) {
            return "计时中";
        }
        if (penalty != null && penalty.cooldownActive(nowEpochMillis)) {
            return "冷却中";
        }
        return "空闲";
    }

    private static String emptyPersonalTrial(PapiRequest.Field field, PluginSettings settings) {
        return switch (field) {
            case TIME -> settings.papiEmptyTime();
            case RANK -> EMPTY_RANK;
            case COUNT -> "0";
            case TRACK -> EMPTY_TRACK;
            default -> settings.papiEmptyName();
        };
    }

    private static String emptyPersonalLastRace(PapiRequest.Field field, PluginSettings settings) {
        return switch (field) {
            case TIME -> settings.papiEmptyTime();
            case RANK -> EMPTY_RANK;
            case LAPS -> EMPTY_LAPS;
            case TRACK -> EMPTY_TRACK;
            case STATUS -> EMPTY_STATUS;
            default -> settings.papiEmptyName();
        };
    }

    private static String emptyPersonalInfo(PapiRequest.Field field, PluginSettings settings) {
        return switch (field) {
            case VIOLATIONS -> "0";
            case COOLDOWN -> NO_COOLDOWN;
            case STATUS -> EMPTY_STATUS;
            default -> settings.papiEmptyName();
        };
    }

    private static String emptyLeaderboard(int rank, PapiRequest.Field field, PluginSettings settings) {
        return switch (field) {
            case NAME -> settings.papiEmptyName();
            case TIME -> settings.papiEmptyTime();
            case LAPS -> EMPTY_LAPS;
            case STATUS -> EMPTY_STATUS;
            case LINE -> rank + ". " + settings.papiEmptyName();
            default -> settings.papiEmptyName();
        };
    }

    private static String emptyCurrentRace(PapiRequest.Field field, PluginSettings settings) {
        return switch (field) {
            case RANK -> EMPTY_STATUS;
            case LAPS -> EMPTY_LAPS;
            case TIME, GAP -> settings.papiEmptyTime();
            case AHEAD -> settings.papiEmptyName();
            case TRACK -> EMPTY_TRACK;
            case STATUS -> EMPTY_STATUS;
            default -> settings.papiEmptyName();
        };
    }

    private static String emptyCurrentTrial(PapiRequest.Field field, PluginSettings settings) {
        return switch (field) {
            case TIME -> settings.papiEmptyTime();
            case STATUS -> "未计时";
            case TRACK -> EMPTY_TRACK;
            case CHECKPOINT -> EMPTY_LAPS;
            default -> settings.papiEmptyName();
        };
    }

    private static String raceStatus(RaceManager.CurrentRaceSnapshot snapshot) {
        RacePhase phase = snapshot.phase();
        return switch (phase) {
            case WAITING -> "等待中";
            case STAGING -> "准备中";
            case COUNTDOWN -> "倒计时";
            case PAUSED -> "已暂停";
            case RUNNING, FINISHED, CANCELLED -> raceStatus(snapshot.status());
        };
    }

    private static String raceStatus(ParticipantStatus status) {
        return switch (status) {
            case FINISHED -> "已完成";
            case DNF -> "未完成";
            case RUNNING -> "比赛中";
            case STAGED, WAITING -> "准备中";
        };
    }
}
