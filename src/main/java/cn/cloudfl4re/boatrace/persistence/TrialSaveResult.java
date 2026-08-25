package cn.cloudfl4re.boatrace.persistence;

import cn.cloudfl4re.boatrace.model.TrialRecord;

import java.util.List;

public record TrialSaveResult(boolean personalBest, long previousNanos, List<TrialRecord> topSeven) {
    public TrialSaveResult {
        topSeven = List.copyOf(topSeven);
    }
}
