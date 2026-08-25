package cn.cloudfl4re.boatrace.util;

import java.util.concurrent.TimeUnit;

public final class TimeFormatter {
    private TimeFormatter() {
    }

    public static String formatNanos(long nanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos));
        long minutes = millis / 60_000L;
        long seconds = millis % 60_000L / 1_000L;
        long fraction = millis % 1_000L;
        return "%02d:%02d.%03d".formatted(minutes, seconds, fraction);
    }
}
