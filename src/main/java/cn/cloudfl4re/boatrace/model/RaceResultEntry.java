package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record RaceResultEntry(
    UUID playerId,
    String playerName,
    int rank,
    long elapsedNanos,
    boolean finished
) {
}
