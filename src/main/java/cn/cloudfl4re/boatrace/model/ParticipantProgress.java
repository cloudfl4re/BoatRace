package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record ParticipantProgress(
    UUID playerId,
    String playerName,
    int joinOrder,
    ParticipantStatus status,
    int nextCheckpoint,
    Point3 position,
    UUID boatId,
    int finishRank,
    long finishNanos
) {
    public static ParticipantProgress waiting(UUID playerId, String playerName, int joinOrder) {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.WAITING, 0, null, null, 0, 0L);
    }

    public ParticipantProgress staged(UUID value) {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.STAGED, 0, position, value, 0, 0L);
    }

    public ParticipantProgress running() {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.RUNNING, 0, position, boatId, 0, 0L);
    }

    public ParticipantProgress at(Point3 value) {
        return new ParticipantProgress(playerId, playerName, joinOrder, status, nextCheckpoint, value, boatId, finishRank, finishNanos);
    }

    public ParticipantProgress advance(Point3 value) {
        return new ParticipantProgress(playerId, playerName, joinOrder, status, nextCheckpoint + 1, value, boatId, finishRank, finishNanos);
    }

    public ParticipantProgress advanceTo(int checkpoint, Point3 value) {
        return new ParticipantProgress(playerId, playerName, joinOrder, status, checkpoint, value, boatId, finishRank, finishNanos);
    }

    public ParticipantProgress finished(int rank, long nanos, Point3 value) {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.FINISHED, nextCheckpoint, value, boatId, rank, nanos);
    }

    public ParticipantProgress dnf() {
        return new ParticipantProgress(playerId, playerName, joinOrder, ParticipantStatus.DNF, nextCheckpoint, position, boatId, 0, 0L);
    }

    public ParticipantProgress resetWaiting() {
        return waiting(playerId, playerName, joinOrder);
    }

    public boolean terminal() {
        return status == ParticipantStatus.FINISHED || status == ParticipantStatus.DNF;
    }
}
