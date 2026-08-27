package cn.cloudfl4re.boatrace.papi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PapiParserTest {
    @Test
    void parsesCombinedNameAndTimeRequests() {
        PapiRequest line = PapiParser.parse("ice-loop_top_1").orElseThrow();
        PapiRequest name = PapiParser.parse("ice-loop_top_7_name").orElseThrow();
        PapiRequest time = PapiParser.parse("ice-loop_top_4_time").orElseThrow();
        PapiRequest fifteenth = PapiParser.parse("trial_ice-loop_top_15_time").orElseThrow();
        PapiRequest count = PapiParser.parse("trial_ice-loop_records").orElseThrow();
        PapiRequest total = PapiParser.parse("trial_ice-loop_total_records").orElseThrow();
        assertEquals(PapiRequest.Field.LINE, line.field());
        assertEquals(PapiRequest.Field.NAME, name.field());
        assertEquals(PapiRequest.Field.TIME, time.field());
        assertEquals(7, name.rank());
        assertEquals(15, fifteenth.rank());
        assertEquals(PapiRequest.Kind.TRIAL_RECORD_COUNT, count.kind());
        assertEquals(PapiRequest.Kind.TRIAL_RECORD_COUNT, total.kind());
    }

    @Test
    void rejectsInvalidRankAndTrack() {
        assertTrue(PapiParser.parse("ice_loop_top_1").isEmpty());
        assertTrue(PapiParser.parse("ice-loop_top_16").isEmpty());
    }

    @Test
    void parsesFormalAndCurrentPlaceholders() {
        PapiRequest formal = PapiParser.parse("race_ice-loop_top_3_laps").orElseThrow();
        PapiRequest raceRank = PapiParser.parse("race_rank").orElseThrow();
        PapiRequest trialTime = PapiParser.parse("trial_time").orElseThrow();
        assertEquals(PapiRequest.Kind.FORMAL_LEADERBOARD, formal.kind());
        assertEquals(PapiRequest.Field.LAPS, formal.field());
        assertEquals(PapiRequest.Field.RANK, raceRank.field());
        assertEquals(PapiRequest.Kind.CURRENT_TRIAL, trialTime.kind());
        assertEquals(PapiRequest.Field.TIME, trialTime.field());
    }

    @Test
    void parsesPersonalPlaceholders() {
        PapiRequest best = PapiParser.parse("my_trial_ice-loop_best").orElseThrow();
        PapiRequest rank = PapiParser.parse("MY_TRIAL_ICE-LOOP_RANK").orElseThrow();
        PapiRequest lastLaps = PapiParser.parse("my_last_laps").orElseThrow();
        PapiRequest violations = PapiParser.parse("my_violations").orElseThrow();
        PapiRequest cooldown = PapiParser.parse("my_cooldown").orElseThrow();
        PapiRequest status = PapiParser.parse("my_status").orElseThrow();

        assertEquals(PapiRequest.Kind.PERSONAL_TRIAL, best.kind());
        assertEquals("ice-loop", best.trackId());
        assertEquals(PapiRequest.Field.TIME, best.field());
        assertEquals(PapiRequest.Field.RANK, rank.field());
        assertEquals(PapiRequest.Kind.PERSONAL_LAST_RACE, lastLaps.kind());
        assertEquals(PapiRequest.Field.LAPS, lastLaps.field());
        assertEquals(PapiRequest.Field.VIOLATIONS, violations.field());
        assertEquals(PapiRequest.Field.COOLDOWN, cooldown.field());
        assertEquals(PapiRequest.Kind.PERSONAL_INFO, status.kind());
        assertEquals(PapiRequest.Field.STATUS, status.field());
    }

    @Test
    void rejectsInvalidPersonalPlaceholders() {
        assertTrue(PapiParser.parse("my_trial_ice_loop_best").isEmpty());
        assertTrue(PapiParser.parse("my_last_checkpoint").isEmpty());
        assertTrue(PapiParser.parse("my_penalty").isEmpty());
    }
}
