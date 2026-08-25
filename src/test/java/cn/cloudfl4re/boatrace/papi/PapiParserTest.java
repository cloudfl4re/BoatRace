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
        assertEquals(PapiRequest.Field.LINE, line.field());
        assertEquals(PapiRequest.Field.NAME, name.field());
        assertEquals(PapiRequest.Field.TIME, time.field());
        assertEquals(7, name.rank());
    }

    @Test
    void rejectsInvalidRankAndTrack() {
        assertTrue(PapiParser.parse("ice_loop_top_1").isEmpty());
        assertTrue(PapiParser.parse("ice-loop_top_8").isEmpty());
    }
}
