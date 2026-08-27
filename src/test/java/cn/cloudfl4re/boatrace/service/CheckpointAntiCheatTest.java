package cn.cloudfl4re.boatrace.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointAntiCheatTest {
    @Test
    void allowsJumpsWithFewerThanFiveSkippedCheckpoints() {
        assertFalse(CheckpointAntiCheat.isSuspiciousJump(1, 6));
    }

    @Test
    void flagsFiveSkippedCheckpoints() {
        assertTrue(CheckpointAntiCheat.isSuspiciousJump(1, 7));
    }

    @Test
    void ignoresNonForwardProgress() {
        assertFalse(CheckpointAntiCheat.isSuspiciousJump(7, 1));
    }
}
