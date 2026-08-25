package cn.cloudfl4re.boatrace.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;

public record PluginSettings(
    int countdownSeconds,
    int backtrackCountdownSeconds,
    long raceTimeoutNanos,
    long trialTimeoutNanos,
    long lobbyIdleTimeoutNanos,
    long cleanupPeriodTicks,
    long particlePeriodTicks,
    double particleViewDistance,
    double particleSpacing,
    int databaseQueueCapacity,
    EntityType raceBoatType,
    String papiEmptyName,
    String papiEmptyTime
) {
    public static PluginSettings load(FileConfiguration config) {
        int countdown = clamp(config.getInt("countdown-seconds", 5), 1, 30);
        int backtrackCountdown = clamp(config.getInt("backtrack-countdown-seconds", 5), 1, 10);
        long raceTimeout = clamp(config.getLong("race-timeout-seconds", 900L), 30L, 86_400L) * 1_000_000_000L;
        long trialTimeout = clamp(config.getLong("trial-timeout-seconds", 900L), 30L, 86_400L) * 1_000_000_000L;
        long lobbyTimeout = clamp(config.getLong("lobby-idle-timeout-seconds", 1800L), 60L, 604_800L) * 1_000_000_000L;
        long cleanupPeriod = clamp(config.getLong("cleanup-period-ticks", 20L), 1L, 1200L);
        long particlePeriod = clamp(config.getLong("particle-period-ticks", 5L), 1L, 200L);
        double viewDistance = clamp(config.getDouble("particle-view-distance", 128.0), 16.0, 512.0);
        double spacing = clamp(config.getDouble("particle-spacing", 1.5), 0.5, 8.0);
        int queue = clamp(config.getInt("database-queue-capacity", 256), 32, 4096);
        EntityType boatType = parseBoat(config.getString("race-boat", "OAK_BOAT"));
        String emptyName = config.getString("papi-empty-name", "暂无记录");
        String emptyTime = config.getString("papi-empty-time", "--:--.---");
        return new PluginSettings(countdown, backtrackCountdown, raceTimeout, trialTimeout, lobbyTimeout, cleanupPeriod, particlePeriod, viewDistance, spacing, queue, boatType, emptyName, emptyTime);
    }

    private static EntityType parseBoat(String value) {
        try {
            EntityType type = EntityType.valueOf(value == null ? "OAK_BOAT" : value.toUpperCase());
            if (type.getEntityClass() != null && Boat.class.isAssignableFrom(type.getEntityClass())) {
                return type;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return EntityType.OAK_BOAT;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
