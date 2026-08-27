package cn.cloudfl4re.boatrace.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PenaltyServiceTest {
    @Test
    void cooldownScheduleMatchesAntiCheatPolicy() {
        long minute = 60_000L;
        assertEquals(10L * minute, PenaltyService.cooldownDurationMillis(1));
        assertEquals(30L * minute, PenaltyService.cooldownDurationMillis(2));
        assertEquals(60L * minute, PenaltyService.cooldownDurationMillis(3));
        assertEquals(2L * 60L * minute, PenaltyService.cooldownDurationMillis(4));
        assertEquals(4L * 60L * minute, PenaltyService.cooldownDurationMillis(5));
        assertEquals(24L * 60L * minute, PenaltyService.cooldownDurationMillis(6));
        assertEquals(24L * 60L * minute, PenaltyService.cooldownDurationMillis(20));
    }
}
