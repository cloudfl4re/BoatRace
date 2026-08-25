package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.model.TrackDraft;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackValidatorTest {
    @Test
    void validTrackPasses() {
        UUID world = UUID.randomUUID();
        TrackDraft draft = new TrackDraft(
            "ice-loop",
            "冰湖赛道",
            world,
            new Cuboid(0, 0, 0, 4, 3, 1),
            List.of(new Cuboid(20, 0, 0, 24, 3, 1)),
            List.of(new StartSlot(2, 1, -2, 0, 0))
        );
        assertTrue(TrackValidator.validate(draft, List.of()).isEmpty());
    }

    @Test
    void overlappingStartIsRejected() {
        UUID world = UUID.randomUUID();
        Track existing = new Track(
            "old",
            "旧赛道",
            world,
            new Cuboid(0, 0, 0, 4, 3, 1),
            List.of(new Cuboid(20, 0, 0, 24, 3, 1)),
            List.of(new StartSlot(2, 1, -2, 0, 0))
        );
        TrackDraft draft = new TrackDraft(
            "new",
            "新赛道",
            world,
            new Cuboid(3, 0, 0, 7, 3, 1),
            List.of(new Cuboid(30, 0, 0, 34, 3, 1)),
            List.of(new StartSlot(5, 1, -2, 0, 0))
        );
        assertTrue(TrackValidator.validate(draft, List.of(existing)).isPresent());
    }
}
