package cn.cloudfl4re.boatrace.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrialRunTest {
    @Test
    void continuousLapResetsCheckpointAndStartTime() {
        TrialRun run = new TrialRun(UUID.randomUUID(), "ice", UUID.randomUUID(), 0, 100L, 1000L);
        TrialRun advanced = run.advance(1200L).advance(1300L);
        assertEquals(2, advanced.nextCheckpoint());
        TrialRun next = advanced.nextLap(500L, 1500L);
        assertEquals(0, next.nextCheckpoint());
        assertEquals(500L, next.lapStartedNanos());
        assertEquals(1500L, next.expiresAtNanos());
    }

    @Test
    void forwardSkipRecordsLaterCheckpoint() {
        TrialRun run = new TrialRun(UUID.randomUUID(), "ice", UUID.randomUUID(), 1, 100L, 1000L);
        assertEquals(3, run.advanceTo(3, 1400L).nextCheckpoint());
    }
}
