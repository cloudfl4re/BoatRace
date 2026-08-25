package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.TrialRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LeaderboardService {
    private final AtomicReference<Map<String, List<TrialRecord>>> leaderboards = new AtomicReference<>(Map.of());

    public void load(Map<String, List<TrialRecord>> values) {
        Map<String, List<TrialRecord>> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        leaderboards.set(Map.copyOf(copy));
    }

    public void update(String trackId, List<TrialRecord> values) {
        leaderboards.updateAndGet(current -> {
            Map<String, List<TrialRecord>> copy = new HashMap<>(current);
            copy.put(trackId, List.copyOf(values));
            return Map.copyOf(copy);
        });
    }

    public List<TrialRecord> top(String trackId) {
        return leaderboards.get().getOrDefault(trackId, List.of());
    }

    public Optional<TrialRecord> at(String trackId, int rank) {
        List<TrialRecord> values = top(trackId);
        return rank >= 1 && rank <= values.size() ? Optional.of(values.get(rank - 1)) : Optional.empty();
    }

    public void remove(String trackId) {
        leaderboards.updateAndGet(current -> {
            Map<String, List<TrialRecord>> copy = new HashMap<>(current);
            copy.remove(trackId);
            return Map.copyOf(copy);
        });
    }
}
