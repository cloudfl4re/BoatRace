package cn.cloudfl4re.boatrace.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatterTest {
    @Test
    void formatsMinutesSecondsAndMillis() {
        assertEquals("02:03.456", TimeFormatter.formatNanos(123_456_000_000L));
    }
}
