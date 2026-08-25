package cn.cloudfl4re.boatrace.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Track(
    String id,
    String displayName,
    UUID worldId,
    Cuboid start,
    List<Cuboid> checkpoints,
    List<StartSlot> slots
) {
    public Track {
        id = Objects.requireNonNull(id);
        displayName = Objects.requireNonNull(displayName);
        worldId = Objects.requireNonNull(worldId);
        start = Objects.requireNonNull(start);
        checkpoints = List.copyOf(checkpoints);
        slots = List.copyOf(slots);
    }

    public Cuboid target(int nextCheckpoint) {
        return nextCheckpoint < checkpoints.size() ? checkpoints.get(nextCheckpoint) : start;
    }
}
