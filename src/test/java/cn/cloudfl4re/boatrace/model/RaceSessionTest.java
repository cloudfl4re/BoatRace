package cn.cloudfl4re.boatrace.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceSessionTest {
    @Test
    void completeRaceAssignsRanksAndDnf() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        RaceSession session = new RaceSession("ABC234", "ice", first, 2, 1000L, 10L);
        assertTrue(session.join(first, "First", 11L));
        assertTrue(session.join(second, "Second", 12L));
        assertFalse(session.join(UUID.randomUUID(), "Third", 13L));
        assertTrue(session.configureLaps(1));
        assertTrue(session.beginStaging(20L));
        assertTrue(session.markStaged(first, UUID.randomUUID()));
        assertTrue(session.markStaged(second, UUID.randomUUID()));
        assertTrue(session.beginCountdown());
        assertTrue(session.beginRunning(100L, 2000L));
        assertTrue(session.advance(first, new Point3(1.0, 0.0, 0.0)));
        assertEquals(1, session.finish(first, 250L, new Point3(2.0, 0.0, 0.0)).orElseThrow().finishRank());
        assertTrue(session.dnf(second).isPresent());
        assertTrue(session.allTerminal());
        assertEquals(2, session.resultEntries().size());
        assertTrue(session.resultEntries().getFirst().finished());
        assertFalse(session.resultEntries().getLast().finished());
    }

    @Test
    void stagingRollbackRestoresWaitingState() {
        UUID owner = UUID.randomUUID();
        RaceSession session = new RaceSession("ABC234", "ice", owner, 1, 1000L, 10L);
        session.join(owner, "Owner", 11L);
        session.configureLaps(1);
        session.beginStaging(20L);
        session.markStaged(owner, UUID.randomUUID());
        session.rollbackStaging(30L);
        assertEquals(RacePhase.WAITING, session.phase());
        assertEquals(ParticipantStatus.WAITING, session.progress(owner).orElseThrow().status());
    }

    @Test
    void forwardSkipAdvancesToLaterCheckpoint() {
        UUID owner = UUID.randomUUID();
        RaceSession session = new RaceSession("ABC234", "ice", owner, 1, 1000L, 10L);
        session.join(owner, "Owner", 11L);
        session.configureLaps(1);
        session.beginStaging(20L);
        session.markStaged(owner, UUID.randomUUID());
        session.beginCountdown();
        session.beginRunning(100L, 2000L);
        assertTrue(session.advanceTo(owner, 3, new Point3(3, 0, 0)));
        assertEquals(3, session.progress(owner).orElseThrow().nextCheckpoint());
    }

    @Test
    void multiLapRaceRequiresEveryLapBeforeFinish() {
        UUID owner = UUID.randomUUID();
        RaceSession session = new RaceSession("ABC234", "ice", owner, 1, 1000L, 10L);
        session.join(owner, "Owner", 11L);
        assertFalse(session.beginStaging(20L));
        assertTrue(session.configureLaps(2));
        assertTrue(session.beginStaging(20L));
        session.markStaged(owner, UUID.randomUUID());
        session.beginCountdown();
        session.beginRunning(100L, 2000L);

        assertTrue(session.finish(owner, 250L, new Point3(2, 0, 0)).isEmpty());
        assertTrue(session.nextLap(owner, new Point3(2, 0, 0)));
        assertEquals(1, session.progress(owner).orElseThrow().completedLaps());
        assertEquals(0, session.progress(owner).orElseThrow().nextCheckpoint());
        assertTrue(session.finish(owner, 300L, new Point3(2, 0, 0)).isPresent());
    }
}
