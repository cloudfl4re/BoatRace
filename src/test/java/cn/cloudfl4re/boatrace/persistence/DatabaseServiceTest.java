package cn.cloudfl4re.boatrace.persistence;

import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.RaceResultEntry;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseServiceTest {
    @TempDir
    Path directory;

    @Test
    void trackBestAndLastRaceRoundTrip() throws Exception {
        UUID world = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Track track = new Track(
            "ice-loop",
            "冰湖赛道",
            world,
            new Cuboid(0, 0, 0, 4, 3, 1),
            List.of(new Cuboid(20, 0, 0, 24, 3, 1)),
            List.of(new StartSlot(2, 1, -2, 0, 0))
        );
        DatabaseService first = new DatabaseService(directory, 64, Logger.getAnonymousLogger());
        first.initialize().get(10, TimeUnit.SECONDS);
        first.saveTrack(track).get(10, TimeUnit.SECONDS);
        TrialSaveResult initial = first.recordTrial(track.id(), player, "Cloud", 5_000L, 100L).get(10, TimeUnit.SECONDS);
        TrialSaveResult slower = first.recordTrial(track.id(), player, "Cloud", 6_000L, 200L).get(10, TimeUnit.SECONDS);
        LastRace race = new LastRace(track.id(), "ABC234", 100L, 200L, List.of(new RaceResultEntry(player, "Cloud", 1, 8_000L, true)));
        first.saveLastRace(race).get(10, TimeUnit.SECONDS);
        assertTrue(initial.personalBest());
        assertFalse(slower.personalBest());
        first.close();

        DatabaseService second = new DatabaseService(directory, 64, Logger.getAnonymousLogger());
        LoadedData loaded = second.initialize().get(10, TimeUnit.SECONDS);
        assertEquals(track, loaded.tracks().get(track.id()));
        assertEquals(5_000L, loaded.leaderboards().get(track.id()).getFirst().bestNanos());
        assertEquals("ABC234", loaded.lastRaces().get(track.id()).code());
        second.close();
    }
}
