package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Failed sign-ins lock a username for one client address for a period. */
class LoginThrottleTest extends ApiIntegrationTest {
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void fiveFailuresLockTheUsernameForThatAddressOnly() throws Exception {
        staffAccount("throttle-a", "Throttle-Password-A");
        clock.fix(START, RESIDENCE_ZONE);
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(attempt("throttle-a", "Wrong-Password-1", "203.0.113.10").getStatus()).isEqualTo(401);
        }
        MockHttpServletResponse locked = attempt("throttle-a", "Wrong-Password-1", "203.0.113.10");
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(locked.getHeader("Retry-After")).isEqualTo("900");
        assertThat(json.readTree(locked.getContentAsString()).get("detail").asString())
                .isEqualTo("Too many failed sign-in attempts. Try again in 15 minutes.");

        // The correct password does not unlock early, and other addresses are unaffected.
        assertThat(attempt("throttle-a", "Throttle-Password-A", "203.0.113.10").getStatus()).isEqualTo(429);
        assertThat(attempt("throttle-a", "Throttle-Password-A", "203.0.113.11").getStatus()).isEqualTo(204);

        clock.fix(START.plus(Duration.ofMinutes(14).plusSeconds(30)), RESIDENCE_ZONE);
        MockHttpServletResponse almost = attempt("throttle-a", "Throttle-Password-A", "203.0.113.10");
        assertThat(almost.getStatus()).isEqualTo(429);
        assertThat(almost.getHeader("Retry-After")).isEqualTo("30");
        assertThat(json.readTree(almost.getContentAsString()).get("detail").asString())
                .isEqualTo("Too many failed sign-in attempts. Try again in 1 minute.");
        clock.fix(START.plus(Duration.ofMinutes(15)), RESIDENCE_ZONE);
        assertThat(attempt("throttle-a", "Throttle-Password-A", "203.0.113.10").getStatus()).isEqualTo(204);
    }

    @Test
    void aSuccessfulSignInClearsEarlierFailures() throws Exception {
        staffAccount("throttle-b", "Throttle-Password-B");
        clock.fix(START, RESIDENCE_ZONE);
        for (int attempt = 1; attempt <= 4; attempt++) attempt("throttle-b", "Wrong-Password-1", "203.0.113.20");
        assertThat(attempt("throttle-b", "Throttle-Password-B", "203.0.113.20").getStatus()).isEqualTo(204);
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(attempt("throttle-b", "Wrong-Password-1", "203.0.113.20").getStatus()).isEqualTo(401);
        }
        assertThat(attempt("throttle-b", "Wrong-Password-1", "203.0.113.20").getStatus()).isEqualTo(429);
    }

    @Test
    void failuresOlderThanThePeriodDoNotCount() throws Exception {
        clock.fix(START, RESIDENCE_ZONE);
        for (int attempt = 1; attempt <= 4; attempt++) attempt("throttle-c", "Wrong-Password-1", "203.0.113.30");
        clock.fix(START.plus(Duration.ofMinutes(16)), RESIDENCE_ZONE);
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(attempt("throttle-c", "Wrong-Password-1", "203.0.113.30").getStatus()).isEqualTo(401);
        }
        assertThat(attempt("throttle-c", "Wrong-Password-1", "203.0.113.30").getStatus()).isEqualTo(429);
    }

    @Test
    void unknownUsernamesAreThrottledLikeRealOnesIgnoringCaseAndSpaces() throws Exception {
        clock.fix(START, RESIDENCE_ZONE);
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(attempt("no-such-user", "Wrong-Password-1", "203.0.113.40").getStatus()).isEqualTo(401);
        }
        assertThat(attempt(" No-Such-User ", "Wrong-Password-1", "203.0.113.40").getStatus()).isEqualTo(429);
        assertThat(attempt("NO-SUCH-USER", "Wrong-Password-1", "203.0.113.40").getStatus()).isEqualTo(429);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void theLockoutLogCannotBeForgedThroughTheUsername(CapturedOutput output) throws Exception {
        clock.fix(START, RESIDENCE_ZONE);
        String forged = "intruder\r\n2026-08-01 INFO Administrator signed in";
        for (int attempt = 1; attempt <= 5; attempt++) attempt(forged, "Wrong-Password-1", "203.0.113.50");

        assertThat(output.getOut())
                .contains("Sign-in for 'intruder__2026-08-01_info_administrator_signed_in' from 203.0.113.50 is locked")
                // Usernames are lower-cased, so this is how an injected line would read.
                .doesNotContain("\n2026-08-01 info administrator signed in");
    }

    private void staffAccount(String username, String password) throws Exception {
        write(post("/api/admin/staff"), admin(), Map.of("username", username, "displayName", "Throttle " + username,
                "password", password), 201);
    }

    private MockHttpServletResponse attempt(String username, String password, String address) throws Exception {
        var initial = mvc.perform(get("/api/session").with(from(address))).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        var csrf = json.readTree(initial.getResponse().getContentAsString());
        return mvc.perform(post("/api/login").session(session).with(from(address))
                        .header(csrf.get("csrfHeader").asString(), csrf.get("csrfToken").asString())
                        .param("username", username).param("password", password))
                .andReturn().getResponse();
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
