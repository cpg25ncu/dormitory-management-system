package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Public endpoints: the frontend, the session bootstrap and health probes. */
class PlatformEndpointsTest extends ApiIntegrationTest {
    @Test
    void frontendAssetsArePublicButOtherPathsAreNot() throws Exception {
        for (String asset : new String[]{"/", "/index.html", "/app.js", "/styles.css", "/favicon.svg"}) {
            mvc.perform(get(asset)).andExpect(status().isOk());
        }
        mvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/report.txt")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbesArePublicWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void demoSessionOffersBothRolesAndTheResidenceTimeZone() throws Exception {
        JsonNode session = session();
        assertThat(session.get("user").isNull()).isTrue();
        assertThat(session.get("demo").asBoolean()).isTrue();
        assertThat(session.get("timeZone").asString()).isEqualTo(RESIDENCE_ZONE.getId());
        assertThat(session.get("demoAccounts").findValuesAsString("role")).containsExactly("ADMIN", "STAFF");
        // Signing in with each advertised account succeeds.
        admin();
        staff();
    }
}
