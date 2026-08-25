package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.Track;

import java.util.OptionalInt;

public final class ForwardCheckpointDetector {
    private ForwardCheckpointDetector() {
    }

    public static OptionalInt highestCrossed(Track track, int nextCheckpoint, Point3 from, Point3 to) {
        int highest = -1;
        for (int index = Math.max(0, nextCheckpoint); index < track.checkpoints().size(); index++) {
            if (track.checkpoints().get(index).crossed(from, to)) {
                highest = index;
            }
        }
        return highest < 0 ? OptionalInt.empty() : OptionalInt.of(highest);
    }
}
