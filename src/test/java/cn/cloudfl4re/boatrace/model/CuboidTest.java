package cn.cloudfl4re.boatrace.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidTest {
    private final Cuboid gate = new Cuboid(0.0, 0.0, 0.0, 4.0, 3.0, 1.0);

    @Test
    void outsideToInsideCrosses() {
        assertTrue(gate.crossed(new Point3(2.0, 1.0, -2.0), new Point3(2.0, 1.0, 0.5)));
    }

    @Test
    void highSpeedOutsideToOutsideCrosses() {
        assertTrue(gate.crossed(new Point3(2.0, 1.0, -10.0), new Point3(2.0, 1.0, 10.0)));
    }

    @Test
    void leavingGateDoesNotCountAgain() {
        assertFalse(gate.crossed(new Point3(2.0, 1.0, 0.5), new Point3(2.0, 1.0, 3.0)));
    }

    @Test
    void missedSegmentDoesNotCross() {
        assertFalse(gate.crossed(new Point3(8.0, 1.0, -2.0), new Point3(8.0, 1.0, 3.0)));
    }
}
