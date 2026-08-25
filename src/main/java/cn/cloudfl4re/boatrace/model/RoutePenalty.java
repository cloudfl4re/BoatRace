package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record RoutePenalty(UUID playerId, UUID boatId, String trackId, int restoreCheckpointIndex) {
}
