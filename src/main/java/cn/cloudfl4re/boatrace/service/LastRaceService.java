package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.LastRace;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class LastRaceService {
    private final AtomicReference<Map<String, LastRace>> races = new AtomicReference<>(Map.of());

    public void load(Map<String, LastRace> values) {
        races.set(Map.copyOf(values));
    }

    public void update(LastRace race) {
        races.updateAndGet(current -> {
            Map<String, LastRace> copy = new HashMap<>(current);
            copy.put(race.trackId(), race);
            return Map.copyOf(copy);
        });
    }

    public Optional<LastRace> get(String trackId) {
        return Optional.ofNullable(races.get().get(trackId));
    }

    public Optional<LastRace> latestForPlayer(UUID playerId) {
        return races.get().values().stream()
            .filter(race -> race.entries().stream().anyMatch(entry -> entry.playerId().equals(playerId)))
            .max(java.util.Comparator.comparingLong(LastRace::endedEpochMillis));
    }

    public void remove(String trackId) {
        races.updateAndGet(current -> {
            Map<String, LastRace> copy = new HashMap<>(current);
            copy.remove(trackId);
            return Map.copyOf(copy);
        });
    }
}
