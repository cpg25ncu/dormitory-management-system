package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Correcting and withdrawing absence records. */
class AbsenceCorrectionsTest extends ApiIntegrationTest {
    @Test
    void correctionsMustFallWithinTheStayAndRecordTheCorrector() throws Exception {
        var admin = admin();
        long room = room(admin, building(admin, "Correction hall"), "C-01", 2);
        long student = student(admin, "CORRECT-1");
        clock.fix(Instant.parse("2026-05-01T02:00:00Z"), RESIDENCE_ZONE);
        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", room), 201);
        long stay = activeStay(admin, "CORRECT-1");
        write(post("/api/absences"), admin, absence(stay, "2026-05-01", "Recorded on arrival day"), 201);
        long id = absenceId(admin, "CORRECT-1", "active");

        clock.fix(Instant.parse("2026-05-05T02:00:00Z"), RESIDENCE_ZONE);
        write(post("/api/absences"), admin, absence(stay, "2026-05-04", "Second absence"), 201);
        assertThat(rejected(put("/api/absences/" + id), admin, correction("2026-04-30", "Before arrival"), 400))
                .isEqualTo("Choose a date during this stay, from 2026-05-01 to 2026-05-05.");
        write(put("/api/absences/" + id), admin, correction("2026-05-06", "Tomorrow"), 400);
        assertThat(rejected(put("/api/absences/" + id), admin, correction("2026-05-04", "Same day as another"), 409))
                .isEqualTo("This resident already has an absence record for that date.");
        write(put("/api/absences/" + id), admin, correction("2026-05-03", "Corrected date"), 204);

        JsonNode corrected = record(admin, id, "active");
        assertThat(corrected.get("absenceDate").asString()).isEqualTo("2026-05-03");
        assertThat(corrected.get("reason").asString()).isEqualTo("Corrected date");
        assertThat(corrected.get("updatedBy").asString()).isEqualTo("System Administrator");
        assertThat(Instant.parse(corrected.get("updatedAt").asString())).isEqualTo(Instant.parse("2026-05-05T02:00:00Z"));

        // After checkout, a correction may still use any day of the closed stay.
        write(post(studentOperation(student, "checkout")), admin, Map.of("reason", "End of stay"), 204);
        clock.fix(Instant.parse("2026-05-10T02:00:00Z"), RESIDENCE_ZONE);
        write(put("/api/absences/" + id), admin, correction("2026-05-05", "Departure day"), 204);
        write(put("/api/absences/" + id), admin, correction("2026-05-06", "After departure"), 400);
    }

    @Test
    void withdrawnRecordsLeaveTheLogAndReturnWhenRecordedAgain() throws Exception {
        var admin = admin();
        var staff = staff();
        long stay = activeStay(staff, "DEMO-003");
        String today = today().toString();
        write(post("/api/absences"), staff, absence(stay, today, "Mistaken entry"), 201);
        long id = absenceId(staff, "DEMO-003", "active");

        write(post("/api/absences/" + id + "/withdraw"), staff, Map.of("reason", "Recorded for the wrong resident"), 204);
        assertThat(rejected(post("/api/absences/" + id + "/withdraw"), staff, Map.of("reason", "Again"), 409))
                .isEqualTo("This absence record is already withdrawn.");
        assertThat(rejected(put("/api/absences/" + id), staff, correction(today, "Edit"), 409))
                .isEqualTo("Withdrawn records cannot be corrected. Record the absence again instead.");
        assertThat(getJson("/api/absences", staff).toString()).doesNotContain("Mistaken entry");
        JsonNode withdrawn = record(admin, id, "withdrawn");
        assertThat(withdrawn.get("status").asString()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.get("withdrawnBy").asString()).isEqualTo("Morgan Reed");
        assertThat(withdrawn.get("withdrawalReason").asString()).isEqualTo("Recorded for the wrong resident");

        write(post("/api/absences"), admin, absence(stay, today, "Confirmed absence"), 201);
        JsonNode restored = record(admin, id, "active");
        assertThat(restored.get("reason").asString()).isEqualTo("Confirmed absence");
        assertThat(restored.get("recordedBy").asString()).isEqualTo("System Administrator");
        assertThat(restored.get("withdrawnAt").isNull()).isTrue();
        mvc.perform(get("/api/absences").param("status", "archived").session(admin.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffCanOnlyCorrectAndWithdrawRecordsInTheirBuildings() throws Exception {
        var admin = admin();
        var staff = staff();
        // DEMO-007 lives in Maple Hall, which is not assigned to the demo staff account.
        long id = absenceId(admin, "DEMO-007", "active");
        write(put("/api/absences/" + id), staff, correction(today().toString(), "Out of scope"), 404);
        write(post("/api/absences/" + id + "/withdraw"), staff, Map.of("reason", "Out of scope"), 404);
        assertThat(record(admin, id, "active").get("reason").asString()).isEqualTo("Off-campus placement");
        write(post("/api/absences/999999/withdraw"), admin, Map.of("reason", "Missing"), 404);
    }

    @Test
    void recordingAWithdrawnAbsenceTwiceAtOnceRestoresItOnce() throws Exception {
        var admin = admin();
        long room = room(admin, building(admin, "Restore race hall"), "R-01", 1);
        long student = student(admin, "RESTORE-1");
        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", room), 201);
        long stay = activeStay(admin, "RESTORE-1");
        String today = today().toString();
        write(post("/api/absences"), admin, absence(stay, today, "Original"), 201);
        long id = absenceId(admin, "RESTORE-1", "active");
        write(post("/api/absences/" + id + "/withdraw"), admin, Map.of("reason", "Withdrawn"), 204);

        var start = new CountDownLatch(1);
        var threads = Executors.newFixedThreadPool(2);
        try {
            var first = threads.submit(() -> {
                start.await();
                return perform(post("/api/absences"), admin, absence(stay, today, "First restore"));
            });
            var second = threads.submit(() -> {
                start.await();
                return perform(post("/api/absences"), admin, absence(stay, today, "Second restore"));
            });
            start.countDown();
            assertThat(new Integer[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            threads.shutdownNow();
        }
        JsonNode all = getJson("/api/absences?status=all", admin);
        long records = 0;
        for (JsonNode row : all) if (row.get("studentNumber").asString().equals("RESTORE-1")) records++;
        assertThat(records).isOne();
        assertThat(record(admin, id, "active").get("reason").asString()).isIn("First restore", "Second restore");
    }

    private long absenceId(Login login, String studentNumber, String status) throws Exception {
        return find(getJson("/api/absences?status=" + status, login), "studentNumber", studentNumber).get("id").asLong();
    }

    private JsonNode record(Login login, long id, String status) throws Exception {
        return find(getJson("/api/absences?status=" + status, login), "id", Long.toString(id));
    }

    private static Map<String, Object> absence(long stay, String date, String reason) {
        return Map.of("stayId", stay, "absenceDate", date, "reason", reason);
    }

    private static Map<String, String> correction(String date, String reason) {
        return Map.of("absenceDate", date, "reason", reason);
    }
}
