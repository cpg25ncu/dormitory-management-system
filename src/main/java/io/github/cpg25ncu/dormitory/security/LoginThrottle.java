package io.github.cpg25ncu.dormitory.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits failed sign-in attempts for each username and client address.
 *
 * <p>After {@code maxFailures} failures within one period, that username is locked for that address for one
 * period. A correct password does not unlock it early. A successful sign-in clears the count. Keying by address as well as username stops one client from locking an
 * account for everyone, and unknown usernames are counted the same way as real ones.
 *
 * <p>The counts are held in memory, so they apply to a single application instance and reset on restart.
 */
@Component
public class LoginThrottle {
    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Clock clock;
    private final int maxFailures;
    private final Duration period;
    private final ConcurrentHashMap<Key, Attempts> attempts = new ConcurrentHashMap<>();

    LoginThrottle(Clock clock, @Value("${dormitory.login.max-failures}") int maxFailures,
                  @Value("${dormitory.login.lockout-period}") Duration period) {
        if (maxFailures < 1 || period.isNegative() || period.isZero()) {
            throw new IllegalStateException("dormitory.login.max-failures and lockout-period must be positive.");
        }
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.period = period;
    }

    /** How long this username stays locked for this address, or empty when it may sign in. */
    public Optional<Duration> lockedFor(String username, String address) {
        Instant now = clock.instant();
        var current = attempts.get(key(username, address));
        if (current == null || current.lockedUntil() == null || !now.isBefore(current.lockedUntil())) return Optional.empty();
        return Optional.of(Duration.between(now, current.lockedUntil()));
    }

    /** Counts a failed attempt and returns the lockout it starts, if any. */
    public Optional<Duration> recordFailure(String username, String address) {
        Instant now = clock.instant();
        Key key = key(username, address);
        var updated = attempts.compute(key, (ignored, current) -> {
            boolean expired = current == null || (current.lockedUntil() == null
                    ? !now.isBefore(current.firstFailure().plus(period))
                    : !now.isBefore(current.lockedUntil()));
            int failures = expired ? 1 : current.failures() + 1;
            Instant first = expired ? now : current.firstFailure();
            return new Attempts(failures, first, failures >= maxFailures ? now.plus(period) : null);
        });
        if (attempts.size() > PRUNE_THRESHOLD) prune(now);
        if (updated.lockedUntil() == null) return Optional.empty();
        if (updated.failures() == maxFailures) {
            log.warn("Sign-in for '{}' from {} is locked for {} minutes after {} failed attempts.",
                    forLog(key.username()), key.address(), Math.max(1, period.toMinutes()), maxFailures);
        }
        return Optional.of(period);
    }

    public void recordSuccess(String username, String address) {
        attempts.remove(key(username, address));
    }

    private void prune(Instant now) {
        attempts.values().removeIf(entry -> entry.lockedUntil() == null
                ? !now.isBefore(entry.firstFailure().plus(period))
                : !now.isBefore(entry.lockedUntil()));
    }

    private static Key key(String username, String address) {
        // Usernames are case-insensitive, as they are for sign-in.
        String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        return new Key(normalized.length() > 80 ? normalized.substring(0, 80) : normalized, address);
    }

    /**
     * A submitted username is untrusted. Characters a username cannot contain are replaced, so a value with line
     * breaks or other control characters cannot forge log lines.
     */
    static String forLog(String username) {
        return username.replaceAll("[^a-z0-9._-]", "_");
    }

    private record Key(String username, String address) {}

    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil) {}
}
