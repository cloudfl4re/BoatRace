package cn.cloudfl4re.boatrace.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RaceSession {
    private final String code;
    private final String trackId;
    private final UUID ownerId;
    private final int capacity;
    private final long createdEpochMillis;
    private final Map<UUID, ParticipantProgress> participants = new LinkedHashMap<>();
    private RacePhase phase = RacePhase.WAITING;
    private int laps;
    private long lastActivityNanos;
    private long goNanos;
    private long goEpochMillis;
    private long pausedNanos;
    private long pauseStartedNanos;
    private boolean firstFinisherClaimed;
    private int joinSequence;
    private int finishSequence;

    public RaceSession(String code, String trackId, UUID ownerId, int capacity, long createdEpochMillis, long nowNanos) {
        this.code = code;
        this.trackId = trackId;
        this.ownerId = ownerId;
        this.capacity = capacity;
        this.createdEpochMillis = createdEpochMillis;
        this.lastActivityNanos = nowNanos;
    }

    public synchronized boolean join(UUID playerId, String playerName, long nowNanos) {
        if (phase != RacePhase.WAITING || participants.size() >= capacity || participants.containsKey(playerId)) {
            return false;
        }
        participants.put(playerId, ParticipantProgress.waiting(playerId, playerName, joinSequence++));
        lastActivityNanos = nowNanos;
        return true;
    }

    public synchronized boolean configureLaps(int value) {
        if (phase != RacePhase.WAITING || value < 1) {
            return false;
        }
        laps = value;
        lastActivityNanos = System.nanoTime();
        return true;
    }

    public synchronized boolean removeWaiting(UUID playerId, long nowNanos) {
        if (phase != RacePhase.WAITING || participants.remove(playerId) == null) {
            return false;
        }
        lastActivityNanos = nowNanos;
        return true;
    }

    public synchronized boolean beginStaging(long nowNanos) {
        if (phase != RacePhase.WAITING || laps < 1 || participants.isEmpty()) {
            return false;
        }
        phase = RacePhase.STAGING;
        lastActivityNanos = nowNanos;
        return true;
    }

    public synchronized boolean markStaged(UUID playerId, UUID boatId) {
        if (phase != RacePhase.STAGING) {
            return false;
        }
        ParticipantProgress current = participants.get(playerId);
        if (current == null || current.status() != ParticipantStatus.WAITING) {
            return false;
        }
        participants.put(playerId, current.staged(boatId));
        return true;
    }

    public synchronized boolean allStaged() {
        return phase == RacePhase.STAGING && !participants.isEmpty()
            && participants.values().stream().allMatch(value -> value.status() == ParticipantStatus.STAGED);
    }

    public synchronized boolean beginCountdown() {
        if (!allStaged()) {
            return false;
        }
        phase = RacePhase.COUNTDOWN;
        return true;
    }

    public synchronized boolean beginRunning(long nanos, long epochMillis) {
        if (phase != RacePhase.COUNTDOWN) {
            return false;
        }
        phase = RacePhase.RUNNING;
        goNanos = nanos;
        goEpochMillis = epochMillis;
        pausedNanos = 0L;
        pauseStartedNanos = 0L;
        firstFinisherClaimed = false;
        participants.replaceAll((id, value) -> value.running());
        return true;
    }

    public synchronized boolean pause(long nowNanos) {
        if (phase != RacePhase.RUNNING) {
            return false;
        }
        phase = RacePhase.PAUSED;
        pauseStartedNanos = nowNanos;
        lastActivityNanos = nowNanos;
        return true;
    }

    public synchronized boolean resume(long nowNanos) {
        if (phase != RacePhase.PAUSED) {
            return false;
        }
        pausedNanos += Math.max(0L, nowNanos - pauseStartedNanos);
        pauseStartedNanos = 0L;
        phase = RacePhase.RUNNING;
        lastActivityNanos = nowNanos;
        return true;
    }

    public synchronized boolean claimFirstFinisher() {
        if (firstFinisherClaimed) {
            return false;
        }
        firstFinisherClaimed = true;
        return true;
    }

    public synchronized long elapsedNanos(long nowNanos) {
        if (goNanos == 0L) {
            return 0L;
        }
        long paused = pausedNanos + (phase == RacePhase.PAUSED ? Math.max(0L, nowNanos - pauseStartedNanos) : 0L);
        return Math.max(0L, nowNanos - goNanos - paused);
    }

    public synchronized void rollbackStaging(long nowNanos) {
        if (phase != RacePhase.STAGING && phase != RacePhase.COUNTDOWN) {
            return;
        }
        phase = RacePhase.WAITING;
        participants.replaceAll((id, value) -> value.resetWaiting());
        lastActivityNanos = nowNanos;
    }

    public synchronized Optional<ParticipantProgress> progress(UUID playerId) {
        return Optional.ofNullable(participants.get(playerId));
    }

    public synchronized String leaderName() {
        return participants.values().stream()
            .filter(value -> value.status() == ParticipantStatus.FINISHED)
            .min(Comparator.comparingInt(ParticipantProgress::finishRank))
            .map(ParticipantProgress::playerName)
            .orElse(null);
    }

    public synchronized long leaderTimeNanos() {
        return participants.values().stream()
            .filter(value -> value.status() == ParticipantStatus.FINISHED)
            .min(Comparator.comparingInt(ParticipantProgress::finishRank))
            .map(ParticipantProgress::finishNanos)
            .orElse(0L);
    }

    public synchronized boolean updatePosition(UUID playerId, Point3 point) {
        ParticipantProgress current = participants.get(playerId);
        if (phase != RacePhase.RUNNING || current == null || current.status() != ParticipantStatus.RUNNING) {
            return false;
        }
        participants.put(playerId, current.at(point));
        return true;
    }

    public synchronized boolean advance(UUID playerId, Point3 point) {
        ParticipantProgress current = participants.get(playerId);
        if (phase != RacePhase.RUNNING || current == null || current.status() != ParticipantStatus.RUNNING) {
            return false;
        }
        participants.put(playerId, current.advance(point));
        return true;
    }

    public synchronized boolean advanceTo(UUID playerId, int nextCheckpoint, Point3 point) {
        return advanceTo(playerId, nextCheckpoint, point, 0L);
    }

    public synchronized boolean advanceTo(UUID playerId, int nextCheckpoint, Point3 point, long elapsedNanos) {
        ParticipantProgress current = participants.get(playerId);
        if (phase != RacePhase.RUNNING || current == null || current.status() != ParticipantStatus.RUNNING || nextCheckpoint <= current.nextCheckpoint()) {
            return false;
        }
        participants.put(playerId, current.advanceTo(nextCheckpoint, point, elapsedNanos));
        return true;
    }

    public synchronized boolean nextLap(UUID playerId, Point3 point) {
        return nextLap(playerId, point, 0L);
    }

    public synchronized boolean nextLap(UUID playerId, Point3 point, long elapsedNanos) {
        ParticipantProgress current = participants.get(playerId);
        if (phase != RacePhase.RUNNING || current == null || current.status() != ParticipantStatus.RUNNING || current.completedLaps() + 1 >= laps) {
            return false;
        }
        participants.put(playerId, current.nextLap(point, elapsedNanos));
        return true;
    }

    public synchronized Optional<ParticipantProgress> finish(UUID playerId, long nowNanos, Point3 point) {
        ParticipantProgress current = participants.get(playerId);
        if (phase != RacePhase.RUNNING || current == null || current.status() != ParticipantStatus.RUNNING || current.completedLaps() + 1 < laps) {
            return Optional.empty();
        }
        ParticipantProgress finished = current.finished(++finishSequence, elapsedNanos(nowNanos), point);
        participants.put(playerId, finished);
        return Optional.of(finished);
    }

    public synchronized Optional<ParticipantProgress> dnf(UUID playerId) {
        return dnf(playerId, 0L);
    }

    public synchronized Optional<ParticipantProgress> dnf(UUID playerId, long nowNanos) {
        ParticipantProgress current = participants.get(playerId);
        if (current == null || current.terminal()) {
            return Optional.empty();
        }
        long elapsed = elapsedNanos(nowNanos);
        ParticipantProgress dnf = current.dnf(elapsed);
        participants.put(playerId, dnf);
        return Optional.of(dnf);
    }

    public synchronized boolean allTerminal() {
        return !participants.isEmpty() && participants.values().stream().allMatch(ParticipantProgress::terminal);
    }

    public synchronized List<ParticipantProgress> participants() {
        return List.copyOf(participants.values());
    }

    public synchronized List<RaceResultEntry> resultEntries() {
        List<ParticipantProgress> values = new ArrayList<>(participants.values());
        values.sort(Comparator
            .comparing((ParticipantProgress value) -> value.status() != ParticipantStatus.FINISHED)
            .thenComparingInt(value -> value.status() == ParticipantStatus.FINISHED ? value.finishRank() : value.joinOrder()));
        return values.stream().map(value -> new RaceResultEntry(
            value.playerId(),
            value.playerName(),
            value.finishRank(),
            value.finishNanos(),
            value.status() == ParticipantStatus.FINISHED,
            value.completedLaps(),
            laps
        )).toList();
    }

    public synchronized void finishPhase() {
        phase = RacePhase.FINISHED;
    }

    public synchronized void cancelPhase() {
        phase = RacePhase.CANCELLED;
    }

    public synchronized boolean cancelIfActive() {
        if (phase == RacePhase.CANCELLED || phase == RacePhase.FINISHED) {
            return false;
        }
        phase = RacePhase.CANCELLED;
        return true;
    }

    public synchronized boolean isPaused() {
        return phase == RacePhase.PAUSED;
    }

    public synchronized void touch(long nowNanos) {
        lastActivityNanos = nowNanos;
    }

    public String code() {
        return code;
    }

    public String trackId() {
        return trackId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public int capacity() {
        return capacity;
    }

    public long createdEpochMillis() {
        return createdEpochMillis;
    }

    public synchronized RacePhase phase() {
        return phase;
    }

    public synchronized long lastActivityNanos() {
        return lastActivityNanos;
    }

    public synchronized long goNanos() {
        return goNanos;
    }

    public synchronized long goEpochMillis() {
        return goEpochMillis;
    }

    public synchronized int size() {
        return participants.size();
    }

    public synchronized int laps() {
        return laps;
    }

    public synchronized boolean member(UUID playerId) {
        return participants.containsKey(playerId);
    }
}
