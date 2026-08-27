package cn.cloudfl4re.boatrace.persistence;

import cn.cloudfl4re.boatrace.model.TrialRecord;

import java.util.List;

public record TrialSaveResult(boolean personalBest, long previousNanos, List<TrialRecord> topRecords, int recordCount) {
    public TrialSaveResult(boolean personalBest, long previousNanos, List<TrialRecord> topRecords) {
        this(personalBest, previousNanos, topRecords, topRecords.size());
    }

    public TrialSaveResult {
        topRecords = List.copyOf(topRecords);
        recordCount = Math.max(0, recordCount);
    }

}
