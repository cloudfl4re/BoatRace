package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record RaceResultEntry(
    UUID playerId,
    String playerName,
    int rank,
    long elapsedNanos,
    boolean finished,
    int completedLaps,
    int totalLaps
) {
    public RaceResultEntry(UUID playerId, String playerName, int rank, long elapsedNanos, boolean finished) {
        this(playerId, playerName, rank, elapsedNanos, finished, 0, 0);
    }
}
