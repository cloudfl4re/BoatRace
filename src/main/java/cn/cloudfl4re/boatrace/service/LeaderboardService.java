package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.TrialRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LeaderboardService {
    private final AtomicReference<State> state = new AtomicReference<>(new State(Map.of(), Map.of()));

    public void load(Map<String, List<TrialRecord>> values) {
        Map<String, Integer> counts = new HashMap<>();
        values.forEach((key, value) -> counts.put(key, value.size()));
        load(values, counts);
    }

    public void load(Map<String, List<TrialRecord>> values, Map<String, Integer> recordCounts) {
        Map<String, List<TrialRecord>> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        Map<String, Integer> counts = new HashMap<>();
        recordCounts.forEach((key, value) -> counts.put(key, Math.max(0, value)));
        copy.forEach((key, value) -> counts.putIfAbsent(key, value.size()));
        state.set(new State(Map.copyOf(copy), Map.copyOf(counts)));
    }

    public void update(String trackId, List<TrialRecord> values) {
        update(trackId, values, values.size());
    }

    public void update(String trackId, List<TrialRecord> values, int recordCount) {
        state.updateAndGet(current -> {
            Map<String, List<TrialRecord>> copy = new HashMap<>(current.leaderboards());
            copy.put(trackId, List.copyOf(values));
            Map<String, Integer> counts = new HashMap<>(current.recordCounts());
            counts.put(trackId, Math.max(0, recordCount));
            return new State(Map.copyOf(copy), Map.copyOf(counts));
        });
    }

    public List<TrialRecord> top(String trackId) {
        return state.get().leaderboards().getOrDefault(trackId, List.of());
    }

    public int recordCount(String trackId) {
        return state.get().recordCounts().getOrDefault(trackId, 0);
    }

    public Optional<TrialRecord> at(String trackId, int rank) {
        List<TrialRecord> values = top(trackId);
        return rank >= 1 && rank <= values.size() ? Optional.of(values.get(rank - 1)) : Optional.empty();
    }

    public void remove(String trackId) {
        state.updateAndGet(current -> {
            Map<String, List<TrialRecord>> copy = new HashMap<>(current.leaderboards());
            copy.remove(trackId);
            Map<String, Integer> counts = new HashMap<>(current.recordCounts());
            counts.remove(trackId);
            return new State(Map.copyOf(copy), Map.copyOf(counts));
        });
    }

    private record State(
        Map<String, List<TrialRecord>> leaderboards,
        Map<String, Integer> recordCounts
    ) {
    }
}
