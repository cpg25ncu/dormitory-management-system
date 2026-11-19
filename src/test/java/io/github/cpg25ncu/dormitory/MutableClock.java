package io.github.cpg25ncu.dormitory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** A system clock that a test can pin to a specific instant and time zone. */
final class MutableClock extends Clock {
    private final ZoneId defaultZone;
    private volatile Clock delegate;

    MutableClock(ZoneId defaultZone) {
        this.defaultZone = defaultZone;
        reset();
    }

    void fix(Instant instant, ZoneId zone) {
        delegate = Clock.fixed(instant, zone);
    }

    void reset() {
        delegate = Clock.system(defaultZone);
    }

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return delegate.withZone(zone);
    }

    @Override
    public Instant instant() {
        return delegate.instant();
    }
}
