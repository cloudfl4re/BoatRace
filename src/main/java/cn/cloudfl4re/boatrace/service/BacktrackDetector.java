package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.BacktrackHit;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.Track;

import java.util.Optional;

public final class BacktrackDetector {
    private BacktrackDetector() {
    }

    public static Optional<BacktrackHit> detect(Track track, int nextCheckpoint, Point3 from, Point3 to) {
        if (nextCheckpoint <= 0) {
            return Optional.empty();
        }
        if (track.start().crossed(from, to)) {
            return Optional.of(new BacktrackHit(0, nextCheckpoint - 1));
        }
        for (int index = 0; index < nextCheckpoint - 1; index++) {
            if (track.checkpoints().get(index).crossed(from, to)) {
                return Optional.of(new BacktrackHit(index + 1, nextCheckpoint - 1));
            }
        }
        return Optional.empty();
    }
}
