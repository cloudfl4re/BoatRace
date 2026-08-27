package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.PlayerPenalty;
import cn.cloudfl4re.boatrace.persistence.DatabaseService;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PenaltyService {
    private static final long[] COOLDOWN_MILLIS = {
        10L * 60L * 1_000L,
        30L * 60L * 1_000L,
        60L * 60L * 1_000L,
        2L * 60L * 60L * 1_000L,
        4L * 60L * 60L * 1_000L,
        24L * 60L * 60L * 1_000L
    };

    private final DatabaseService database;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, PlayerPenalty> penalties = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> names = new ConcurrentHashMap<>();

    public PenaltyService(DatabaseService database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void load(Map<UUID, PlayerPenalty> loaded) {
        penalties.clear();
        names.clear();
        loaded.forEach((playerId, penalty) -> {
            penalties.put(playerId, penalty);
            rememberName(penalty.playerId(), penalty.playerName());
        });
    }

    public Optional<PlayerPenalty> get(UUID playerId) {
        return Optional.ofNullable(penalties.get(playerId));
    }

    public boolean isBlocked(UUID playerId) {
        PlayerPenalty penalty = penalties.get(playerId);
        return penalty != null && penalty.blocked(System.currentTimeMillis());
    }

    public Optional<PlayerPenalty> active(UUID playerId) {
        PlayerPenalty penalty = penalties.get(playerId);
        if (penalty == null || !penalty.blocked(System.currentTimeMillis())) {
            return Optional.empty();
        }
        return Optional.of(penalty);
    }

    public void rememberName(UUID playerId, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        PlayerPenalty current = penalties.get(playerId);
        if (current == null) {
            return;
        }
        if (current.playerName() != null && !current.playerName().isBlank()
            && !current.playerName().equalsIgnoreCase(playerName)) {
            names.remove(current.playerName().toLowerCase(Locale.ROOT), playerId);
        }
        names.put(playerName.toLowerCase(Locale.ROOT), playerId);
        if (!playerName.equals(current.playerName())) {
            PlayerPenalty updated = current.withName(playerName);
            penalties.replace(playerId, current, updated);
            persist(updated);
        }
    }

    public UUID resolveReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String value = reference.trim();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
        }
        return names.get(value.toLowerCase(Locale.ROOT));
    }

    public String displayName(UUID playerId, String fallback) {
        PlayerPenalty penalty = penalties.get(playerId);
        if (penalty != null && penalty.playerName() != null && !penalty.playerName().isBlank()) {
            return penalty.playerName();
        }
        if (fallback == null || fallback.isBlank()) {
            return "目标玩家";
        }
        try {
            UUID.fromString(fallback.trim());
            return "目标玩家";
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public PlayerPenalty recordAntiCheat(UUID playerId, String playerName) {
        rememberName(playerId, playerName);
        long now = System.currentTimeMillis();
        PlayerPenalty updated = penalties.compute(playerId, (ignored, current) -> {
            int count = current == null ? 1 : current.violationCount() + 1;
            long until = now + cooldownDurationMillis(count);
            boolean adminBanned = current != null && current.adminBanned();
            return new PlayerPenalty(playerId, playerName, count, until, adminBanned);
        });
        rememberName(playerId, playerName);
        persist(updated);
        return updated;
    }

    public PlayerPenalty adminBan(UUID playerId, String playerName) {
        rememberName(playerId, playerName);
        PlayerPenalty updated = penalties.compute(playerId, (ignored, current) -> new PlayerPenalty(
            playerId,
            playerName == null || playerName.isBlank() ? current == null ? "" : current.playerName() : playerName,
            current == null ? 0 : current.violationCount(),
            0L,
            true
        ));
        rememberName(playerId, updated.playerName());
        persist(updated);
        return updated;
    }

    public boolean unban(UUID playerId) {
        PlayerPenalty removed = penalties.remove(playerId);
        if (removed == null) {
            return false;
        }
        if (removed.playerName() != null && !removed.playerName().isBlank()) {
            names.remove(removed.playerName().toLowerCase(Locale.ROOT), playerId);
        }
        database.deletePenalty(playerId).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Failed to remove BoatRace player penalty", failure);
            return null;
        });
        return true;
    }

    public String remaining(PlayerPenalty penalty, long nowEpochMillis) {
        if (penalty.adminBanned()) {
            return "永久";
        }
        long remaining = Math.max(0L, penalty.cooldownUntilEpochMillis() - nowEpochMillis);
        long seconds = (remaining + 999L) / 1_000L;
        if (seconds < 60L) {
            return seconds + "秒";
        }
        long minutes = (seconds + 59L) / 60L;
        if (minutes < 60L) {
            return minutes + "分钟";
        }
        long hours = (minutes + 59L) / 60L;
        if (hours < 24L) {
            return hours + "小时";
        }
        return ((hours + 23L) / 24L) + "天";
    }

    public Map<UUID, PlayerPenalty> snapshot() {
        return Map.copyOf(penalties);
    }

    public static long cooldownDurationMillis(int violationCount) {
        int index = Math.min(Math.max(violationCount, 1), COOLDOWN_MILLIS.length) - 1;
        return COOLDOWN_MILLIS[index];
    }

    public void shutdown() {
        penalties.clear();
        names.clear();
    }

    private void persist(PlayerPenalty penalty) {
        database.savePenalty(penalty).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Failed to save BoatRace player penalty", failure);
            return null;
        });
    }
}
