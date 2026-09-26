package io.github.khajamohammeddev.configadmin.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Time formatting for the dashboard templates, registered as {@code configAdminTime}. */
public class TimeFormat {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final Clock clock;

    public TimeFormat(Clock clock) {
        this.clock = clock;
    }

    /** e.g. "12s ago", "3m ago", "2h ago". */
    public String ago(Instant instant) {
        if (instant == null) {
            return "never";
        }
        long seconds = Math.max(0, Duration.between(instant, clock.instant()).toSeconds());
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3600) {
            return seconds / 60 + "m ago";
        }
        if (seconds < 86400) {
            return seconds / 3600 + "h ago";
        }
        return seconds / 86400 + "d ago";
    }

    public String timestamp(Instant instant) {
        return instant == null ? "" : TIMESTAMP.format(instant);
    }
}
