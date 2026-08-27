package cn.cloudfl4re.boatrace.persistence;

import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.LastRace;
import cn.cloudfl4re.boatrace.model.PersonalTrialStat;
import cn.cloudfl4re.boatrace.model.PlayerPenalty;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        for (int index = 0; index < 15; index++) {
            first.recordTrial(track.id(), UUID.randomUUID(), "Racer" + index, 10_000L + index, 300L + index)
                .get(10, TimeUnit.SECONDS);
        }
        TrialDeleteResult deleted = first.deleteTrialRecord(track.id(), "Racer14").get(10, TimeUnit.SECONDS);
        assertTrue(deleted.removed());
        assertEquals(15, deleted.recordCount());
        assertEquals(15, deleted.topRecords().size());
        TrialDeleteResult missing = first.deleteTrialRecord(track.id(), "NotAPlayer").get(10, TimeUnit.SECONDS);
        assertFalse(missing.removed());
        assertEquals(15, missing.recordCount());
        LastRace race = new LastRace(track.id(), "ABC234", 100L, 200L, List.of(new RaceResultEntry(player, "Cloud", 1, 8_000L, true)));
        first.saveLastRace(race).get(10, TimeUnit.SECONDS);
        assertTrue(initial.personalBest());
        assertFalse(slower.personalBest());
        first.close();

        DatabaseService second = new DatabaseService(directory, 64, Logger.getAnonymousLogger());
        LoadedData loaded = second.initialize().get(10, TimeUnit.SECONDS);
        assertEquals(track, loaded.tracks().get(track.id()));
        assertEquals(5_000L, loaded.leaderboards().get(track.id()).getFirst().bestNanos());
        assertEquals(15, loaded.leaderboards().get(track.id()).size());
        assertEquals(15, loaded.leaderboardRecordCounts().get(track.id()));
        assertEquals("ABC234", loaded.lastRaces().get(track.id()).code());
        second.close();
    }

    @Test
    void personalTrialReportsLeaderboardRankWithTieBreakers() throws Exception {
        UUID world = UUID.randomUUID();
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID thirdPlayer = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Track track = new Track(
            "ice-loop",
            "冰湖赛道",
            world,
            new Cuboid(0, 0, 0, 4, 3, 1),
            List.of(new Cuboid(20, 0, 0, 24, 3, 1)),
            List.of(new StartSlot(2, 1, -2, 0, 0))
        );
        DatabaseService database = new DatabaseService(directory, 64, Logger.getAnonymousLogger());
        database.initialize().get(10, TimeUnit.SECONDS);
        database.saveTrack(track).get(10, TimeUnit.SECONDS);
        database.recordTrial(track.id(), firstPlayer, "First", 4_000L, 200L).get(10, TimeUnit.SECONDS);
        database.recordTrial(track.id(), secondPlayer, "Second", 5_000L, 100L).get(10, TimeUnit.SECONDS);
        database.recordTrial(track.id(), thirdPlayer, "Third", 5_000L, 100L).get(10, TimeUnit.SECONDS);

        PersonalTrialStat second = database.findPersonalTrial(track.id(), secondPlayer).get(10, TimeUnit.SECONDS);
        PersonalTrialStat third = database.findPersonalTrial(track.id(), thirdPlayer).get(10, TimeUnit.SECONDS);
        PersonalTrialStat missing = database.findPersonalTrial(track.id(), UUID.randomUUID()).get(10, TimeUnit.SECONDS);

        assertEquals(2, second.rank());
        assertEquals(3, third.rank());
        assertEquals(3, second.totalRecords());
        assertEquals(5_000L, second.bestNanos());
        assertNull(missing);
        database.close();
    }

    @Test
    void playerPenaltyRoundTrip() throws Exception {
        UUID player = UUID.randomUUID();
        DatabaseService first = new DatabaseService(directory, 64, Logger.getAnonymousLogger());
        first.initialize().get(10, TimeUnit.SECONDS);
        first.savePenalty(new PlayerPenalty(player, "Racer", 3, 123_456L, false)).get(10, TimeUnit.SECONDS);
        first.close();

        DatabaseService second = new DatabaseService(directory, 64, Logger.getAnonymousLogger());
        LoadedData loaded = second.initialize().get(10, TimeUnit.SECONDS);
        assertEquals(new PlayerPenalty(player, "Racer", 3, 123_456L, false), loaded.penalties().get(player));
        second.deletePenalty(player).get(10, TimeUnit.SECONDS);
        second.close();
    }
}
