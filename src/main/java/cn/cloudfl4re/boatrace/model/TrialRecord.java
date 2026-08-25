package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record TrialRecord(UUID playerId, String playerName, long bestNanos, long achievedEpochMillis) {
}
