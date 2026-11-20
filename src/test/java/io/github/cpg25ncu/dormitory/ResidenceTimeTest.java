package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Calendar days follow the residence's time zone while stored timestamps stay in UTC. */
class ResidenceTimeTest extends ApiIntegrationTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void absenceDatesUseTheResidenceDayRatherThanTheUtcDay() throws Exception {
        // 06:30 on 2 March in Shanghai is still 1 March in UTC.
        clock.fix(Instant.parse("2026-03-01T22:30:00Z"), SHANGHAI);
        var admin = admin();
        long room = room(admin, building(admin, "Early morning hall"), "E-01", 2);
        long student = student(admin, "EARLY-1");
        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", room), 201);
        long stay = activeStay(admin, "EARLY-1");

        assertThat(session().get("timeZone").asString()).isEqualTo("Asia/Shanghai");
        write(post("/api/absences"), admin, absence(stay, "2026-03-02", "Recorded on the local day"), 201);
        write(post("/api/absences"), admin, absence(stay, "2026-03-03", "Tomorrow"), 400);
        write(post("/api/absences"), admin, absence(stay, "2026-03-01", "Before the check-in day"), 404);
    }

    @Test
    void housingTimestampsAreStoredAndReturnedInUtc() throws Exception {
        Instant checkIn = Instant.parse("2026-04-10T16:15:30.123456789Z");
        Instant checkOut = Instant.parse("2026-04-12T01:05:00Z");
        var admin = admin();
        long room = room(admin, building(admin, "Timestamp hall"), "T-01", 1);
        long student = student(admin, "TIMESTAMP-1");

        clock.fix(checkIn, SHANGHAI);
        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", room), 201);
        clock.fix(checkOut, ZoneId.of("America/New_York"));
        write(post(studentOperation(student, "checkout")), admin, Map.of("reason", "End of term"), 204);

        JsonNode departure = find(items("/api/admin/housing/history?q=TIMESTAMP-1", admin), "studentNumber", "TIMESTAMP-1");
        assertThat(Instant.parse(departure.get("checkInAt").asString()))
                .isEqualTo(Instant.parse("2026-04-10T16:15:30.123456Z"));
        assertThat(Instant.parse(departure.get("checkOutAt").asString())).isEqualTo(checkOut);
    }

    private static Map<String, Object> absence(long stay, String date, String reason) {
        return Map.of("stayId", stay, "absenceDate", date, "reason", reason);
    }
}
