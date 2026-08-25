package cn.cloudfl4re.boatrace.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TrackDraft(
    String id,
    String displayName,
    UUID worldId,
    Cuboid start,
    List<Cuboid> checkpoints,
    List<StartSlot> slots
) {
    public TrackDraft {
        checkpoints = List.copyOf(checkpoints);
        slots = List.copyOf(slots);
    }

    public static TrackDraft empty(String id, String displayName) {
        return new TrackDraft(id, displayName, null, null, List.of(), List.of());
    }

    public static TrackDraft from(Track track) {
        return new TrackDraft(track.id(), track.displayName(), track.worldId(), track.start(), track.checkpoints(), track.slots());
    }

    public TrackDraft withStart(UUID selectedWorld, Cuboid gate) {
        return new TrackDraft(id, displayName, selectedWorld, gate, checkpoints, slots);
    }

    public TrackDraft addCheckpoint(Cuboid gate) {
        List<Cuboid> values = new ArrayList<>(checkpoints);
        values.add(gate);
        return new TrackDraft(id, displayName, worldId, start, values, slots);
    }

    public TrackDraft setCheckpoint(int index, Cuboid gate) {
        List<Cuboid> values = new ArrayList<>(checkpoints);
        values.set(index, gate);
        return new TrackDraft(id, displayName, worldId, start, values, slots);
    }

    public TrackDraft removeCheckpoint(int index) {
        List<Cuboid> values = new ArrayList<>(checkpoints);
        values.remove(index);
        return new TrackDraft(id, displayName, worldId, start, values, slots);
    }

    public TrackDraft moveCheckpoint(int from, int to) {
        List<Cuboid> values = new ArrayList<>(checkpoints);
        Cuboid value = values.remove(from);
        values.add(to, value);
        return new TrackDraft(id, displayName, worldId, start, values, slots);
    }

    public TrackDraft addSlot(StartSlot slot) {
        List<StartSlot> values = new ArrayList<>(slots);
        values.add(slot);
        return new TrackDraft(id, displayName, worldId, start, checkpoints, values);
    }

    public TrackDraft removeSlot(int index) {
        List<StartSlot> values = new ArrayList<>(slots);
        values.remove(index);
        return new TrackDraft(id, displayName, worldId, start, checkpoints, values);
    }

    public Track toTrack() {
        return new Track(id, displayName, worldId, start, checkpoints, slots);
    }
}
