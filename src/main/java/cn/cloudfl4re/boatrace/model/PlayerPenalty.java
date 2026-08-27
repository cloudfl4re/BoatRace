package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record PlayerPenalty(
    UUID playerId,
    String playerName,
    int violationCount,
    long cooldownUntilEpochMillis,
    boolean adminBanned
) {
    public PlayerPenalty {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        playerName = playerName == null ? "" : playerName;
        violationCount = Math.max(0, violationCount);
        cooldownUntilEpochMillis = Math.max(0L, cooldownUntilEpochMillis);
    }

    public boolean cooldownActive(long nowEpochMillis) {
        return cooldownUntilEpochMillis > Math.max(0L, nowEpochMillis);
    }

    public boolean blocked(long nowEpochMillis) {
        return adminBanned || cooldownActive(nowEpochMillis);
    }

    public PlayerPenalty withName(String name) {
        return new PlayerPenalty(playerId, name, violationCount, cooldownUntilEpochMillis, adminBanned);
    }
}
