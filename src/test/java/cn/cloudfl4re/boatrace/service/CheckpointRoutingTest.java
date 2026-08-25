package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.BacktrackHit;
import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointRoutingTest {
    private final Track track = new Track(
        "route",
        "Route",
        UUID.randomUUID(),
        new Cuboid(0, 0, 0, 2, 3, 2),
        List.of(
            new Cuboid(10, 0, 0, 12, 3, 2),
            new Cuboid(20, 0, 0, 22, 3, 2),
            new Cuboid(30, 0, 0, 32, 3, 2),
            new Cuboid(40, 0, 0, 42, 3, 2)
        ),
        List.of(new StartSlot(1, 1, -2, 0, 0))
    );

    @Test
    void laterCheckpointSkipsMissingIntermediateCheckpoint() {
        int crossed = ForwardCheckpointDetector.highestCrossed(
            track,
            1,
            new Point3(15, 1, 1),
            new Point3(33, 1, 1)
        ).orElseThrow();
        assertEquals(2, crossed);
    }

    @Test
    void crossingOlderCheckpointReturnsLastRecordedCheckpoint() {
        BacktrackHit hit = BacktrackDetector.detect(
            track,
            3,
            new Point3(25, 1, 1),
            new Point3(21, 1, 1)
        ).orElseThrow();
        assertEquals(2, hit.crossedOrder());
        assertEquals(2, hit.restoreCheckpointIndex());
    }

    @Test
    void crossingLastRecordedCheckpointAgainIsAllowed() {
        assertTrue(BacktrackDetector.detect(
            track,
            3,
            new Point3(35, 1, 1),
            new Point3(31, 1, 1)
        ).isEmpty());
    }
}
