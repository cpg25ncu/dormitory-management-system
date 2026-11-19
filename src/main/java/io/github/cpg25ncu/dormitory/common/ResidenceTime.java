package io.github.cpg25ncu.dormitory.common;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Calendar and timestamp rules for the residence.
 *
 * <p>The database stores timestamps as UTC values. Calendar days, such as the date of an absence,
 * follow the residence's configured time zone so that "today" matches the day on site.
 */
@Component
public class ResidenceTime {
    private final Clock clock;

    ResidenceTime(Clock clock) {
        this.clock = clock;
    }

    public ZoneId zone() {
        return clock.getZone();
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** The current time as a UTC database timestamp. */
    public LocalDateTime now() {
        return utc(clock.instant());
    }

    /** The UTC database timestamp at which a local calendar day begins. */
    public LocalDateTime startOf(LocalDate day) {
        return utc(day.atStartOfDay(zone()).toInstant());
    }

    private static LocalDateTime utc(Instant instant) {
        // TIMESTAMP(6) columns keep microseconds.
        return LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    @Configuration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean
        Clock residenceClock(@Value("${dormitory.time-zone:}") String timeZone) {
            if (timeZone.isBlank()) return Clock.systemDefaultZone();
            try {
                return Clock.system(ZoneId.of(timeZone.strip()));
            } catch (DateTimeException exception) {
                throw new IllegalStateException(
                        "DORM_TIME_ZONE must be an IANA time zone such as Asia/Shanghai or Europe/London.", exception);
            }
        }
    }
}
