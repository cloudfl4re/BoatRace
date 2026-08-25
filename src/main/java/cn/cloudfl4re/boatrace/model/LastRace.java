package cn.cloudfl4re.boatrace.model;

import java.util.List;

public record LastRace(
    String trackId,
    String code,
    long startedEpochMillis,
    long endedEpochMillis,
    List<RaceResultEntry> entries
) {
    public LastRace {
        entries = List.copyOf(entries);
    }
}
