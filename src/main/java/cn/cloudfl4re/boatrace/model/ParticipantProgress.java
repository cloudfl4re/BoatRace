package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record ParticipantProgress(
    UUID playerId,
    String playerName,
    int joinOrder,
    ParticipantStatus status,
    int nextCheckpoint,
    int completedLaps,
    Point3 position,
    UUID boatId,
    int finishRank,
    long finishNanos,
    long progressNanos
) {
    public static ParticipantProgress waiting(UUID playerId, String playerName, int joinOrder) {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.WAITING, 0, 0, null, null, 0, 0L, 0L);
    }

    public ParticipantProgress staged(UUID value) {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.STAGED, 0, 0, position, value, 0, 0L, 0L);
    }

    public ParticipantProgress running() {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.RUNNING, 0, 0, position, boatId, 0, 0L, 0L);
    }

    public ParticipantProgress at(Point3 value) {
        return new ParticipantProgress(playerId, playerName, joinOrder, status, nextCheckpoint, completedLaps, value, boatId, finishRank, finishNanos, progressNanos);
    }

    public ParticipantProgress advance(Point3 value) {
        return advance(value, progressNanos);
    }

    public ParticipantProgress advance(Point3 value, long elapsedNanos) {
        return new ParticipantProgress(playerId, playerName, joinOrder, status, nextCheckpoint + 1, completedLaps, value, boatId, finishRank, finishNanos, elapsedNanos);
    }

    public ParticipantProgress advanceTo(int checkpoint, Point3 value) {
        return advanceTo(checkpoint, value, progressNanos);
    }

    public ParticipantProgress advanceTo(int checkpoint, Point3 value, long elapsedNanos) {
        return new ParticipantProgress(playerId, playerName, joinOrder, status, checkpoint, completedLaps, value, boatId, finishRank, finishNanos, elapsedNanos);
    }

    public ParticipantProgress nextLap(Point3 value) {
        return nextLap(value, progressNanos);
    }

    public ParticipantProgress nextLap(Point3 value, long elapsedNanos) {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.RUNNING, 0, completedLaps + 1, value, boatId, 0, 0L, elapsedNanos);
    }

    public ParticipantProgress finished(int rank, long nanos, Point3 value) {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.FINISHED, nextCheckpoint, completedLaps + 1, value, boatId, rank, nanos, nanos);
    }

    public ParticipantProgress dnf() {
        return dnf(0L);
    }

    public ParticipantProgress dnf(long nanos) {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.DNF, nextCheckpoint, completedLaps, position, boatId, 0, nanos, progressNanos);
    }

    public ParticipantProgress resetWaiting() {
        return waiting(playerId, playerName, joinOrder);
    }

    public boolean terminal() {
        return status == ParticipantStatus.FINISHED || status == ParticipantStatus.DNF;
    }
}
