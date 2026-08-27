package cn.cloudfl4re.boatrace.persistence;

import cn.cloudfl4re.boatrace.model.TrialRecord;

import java.util.List;

public record TrialDeleteResult(int deletedCount, List<TrialRecord> topRecords, int recordCount) {
    public TrialDeleteResult {
        deletedCount = Math.max(0, deletedCount);
        topRecords = List.copyOf(topRecords);
        recordCount = Math.max(0, recordCount);
    }

    public boolean removed() {
        return deletedCount > 0;
    }
}
