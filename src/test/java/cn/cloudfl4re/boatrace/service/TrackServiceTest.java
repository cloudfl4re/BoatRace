package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackServiceTest {
    @Test
    void spatialIndexFindsHighSpeedStartCrossing() {
        UUID world = UUID.randomUUID();
        Track track = new Track(
            "ice",
            "Ice",
            world,
            new Cuboid(31, 0, 0, 33, 3, 2),
            List.of(new Cuboid(50, 0, 0, 52, 3, 2)),
            List.of(new StartSlot(32, 1, -2, 0, 0))
        );
        TrackService service = new TrackService();
        service.load(Map.of(track.id(), track));
        assertEquals(List.of(track), service.crossedStarts(world, new Point3(0, 1, 1), new Point3(64, 1, 1)));
    }
}
