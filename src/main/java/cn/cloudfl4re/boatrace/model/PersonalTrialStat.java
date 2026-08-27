package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record PersonalTrialStat(
    String trackId,
    UUID playerId,
    String playerName,
    long bestNanos,
    long achievedAtEpochMillis,
    int rank,
    int totalRecords
) {
    public PersonalTrialStat {
        if (trackId == null || trackId.isBlank()) {
            throw new IllegalArgumentException("trackId cannot be blank");
        }
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        playerName = playerName == null ? "" : playerName;
        bestNanos = Math.max(0L, bestNanos);
        achievedAtEpochMillis = Math.max(0L, achievedAtEpochMillis);
        rank = Math.max(1, rank);
        totalRecords = Math.max(rank, totalRecords);
    }
}
