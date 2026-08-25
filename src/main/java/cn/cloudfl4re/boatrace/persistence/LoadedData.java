package cn.cloudfl4re.boatrace.persistence;

import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.model.TrialRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record LoadedData(
    Map<String, Track> tracks,
    Map<String, List<TrialRecord>> leaderboards,
    Map<String, LastRace> lastRaces,
    Set<UUID> ownedBoats
) {
}
