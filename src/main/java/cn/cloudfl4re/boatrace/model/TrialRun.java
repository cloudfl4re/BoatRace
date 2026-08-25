package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record TrialRun(
    UUID playerId,
    String trackId,
    UUID boatId,
    int nextCheckpoint,
    long lapStartedNanos,
    long expiresAtNanos
) {
    public TrialRun advance(long expiry) {
        return new TrialRun(playerId, trackId, boatId, nextCheckpoint + 1, lapStartedNanos, expiry);
    }

    public TrialRun advanceTo(int value, long expiry) {
        return new TrialRun(playerId, trackId, boatId, value, lapStartedNanos, expiry);
    }

    public TrialRun nextLap(long started, long expiry) {
        return new TrialRun(playerId, trackId, boatId, 0, started, expiry);
    }
}
