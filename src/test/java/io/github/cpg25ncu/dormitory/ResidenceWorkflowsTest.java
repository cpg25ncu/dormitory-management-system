package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class ResidenceWorkflowsTest extends ApiIntegrationTest {
    @Test
    void unauthenticatedAndUnprotectedWritesAreRejected() throws Exception {
        mvc.perform(get("/api/admin/students")).andExpect(status().isUnauthorized());
        var admin = admin();
        mvc.perform(post("/api/admin/students").session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(studentInput("NO-CSRF"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/").session(admin.session())).andExpect(status().isOk());
    }

    @Test
    void staffCanOnlySeeAndRecordAbsencesForAssignedBuildings() throws Exception {
        var admin = admin();
        var staff = staff();
        mvc.perform(get("/api/admin/students").session(staff.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/buildings").session(staff.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Cedar Hall"));
        assertThat(getJson("/api/rooms", staff).size()).isEqualTo(3);
        assertThat(items("/api/absences?size=100", staff).toString()).doesNotContain("Maple Hall");

        long assignedStay = activeStay(staff, "DEMO-002");
        long otherStay = activeStay(admin, "DEMO-006");
        String today = today().toString();
        write(post("/api/absences"), staff, Map.of("stayId", otherStay, "absenceDate", today, "reason", "No access"), 404);
        write(post("/api/absences"), staff, Map.of("stayId", assignedStay, "absenceDate", today, "reason", "Approved leave"), 201);
        write(post("/api/absences"), staff, Map.of("stayId", assignedStay, "absenceDate", today, "reason", "Duplicate entry"), 409);
        assertThat(items("/api/absences?size=100", admin).toString()).contains("Approved leave");
    }

    @Test
    void roomTransfersAndCheckoutPreserveOccupancyAndHistory() throws Exception {
        var admin = admin();
        long building = building(admin, "Transfer test hall");
        long originalRoom = room(admin, building, "A-01", 1);
        long destination = room(admin, building, "A-02", 1);
        long student = student(admin, "TRANSFER-1");
        long other = student(admin, "TRANSFER-2");

        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", originalRoom), 201);
        write(post("/api/absences"), admin, Map.of("stayId", activeStay(admin, "TRANSFER-1"), "reason", "First-day absence",
                "absenceDate", today().toString()), 201);
        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", destination), 409);
        write(post(studentOperation(other, "check-in")), admin, Map.of("roomId", destination), 201);
        write(post(studentOperation(student, "transfer")), admin, Map.of("roomId", destination), 409);
        assertThat(studentRecord(admin, "TRANSFER-1").get("roomId").asLong())
                .isEqualTo(originalRoom);

        write(post(studentOperation(other, "checkout")), admin, Map.of("reason", "Room change"), 204);
        write(post(studentOperation(student, "transfer")), admin, Map.of("roomId", destination), 204);
        assertThat(occupied(admin, originalRoom)).isZero();
        assertThat(occupied(admin, destination)).isOne();
        write(post(studentOperation(student, "checkout")), admin, Map.of("reason", "End of residence"), 204);
        write(post(studentOperation(student, "checkout")), admin, Map.of("reason", "Repeated checkout"), 409);
        write(delete("/api/admin/students/" + student), admin, null, 409);

        JsonNode history = items("/api/admin/housing/history?size=100&q=TRANSFER-1", admin);
        assertThat(history.toString()).contains("TRANSFER", "CHECKOUT", "End of residence").doesNotContain("Repeated checkout");
        assertThat(occupied(admin, destination)).isZero();
    }

    @Test
    void concurrentCheckInsCannotAllocateTheSameLastBed() throws Exception {
        var admin = admin();
        long building = building(admin, "Concurrent test hall");
        long room = room(admin, building, "C-01", 1);
        long first = student(admin, "CONCURRENT-1");
        long second = student(admin, "CONCURRENT-2");
        var start = new CountDownLatch(1);
        var threads = Executors.newFixedThreadPool(2);
        try {
            var firstResult = threads.submit(() -> {
                start.await();
                return perform(post(studentOperation(first, "check-in")), admin, Map.of("roomId", room));
            });
            var secondResult = threads.submit(() -> {
                start.await();
                return perform(post(studentOperation(second, "check-in")), admin, Map.of("roomId", room));
            });
            start.countDown();
            assertThat(new Integer[]{firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            threads.shutdownNow();
        }
        assertThat(occupied(admin, room)).isOne();
    }

    @Test
    void passwordResetAndAccountDisableRevokeExistingSessions() throws Exception {
        var admin = admin();
        write(post("/api/admin/staff"), admin, Map.of("username", "session-test", "displayName", "Session Test",
                "password", "Original-Staff-Password"), 201);
        long id = find(getJson("/api/admin/staff", admin), "username", "session-test").get("id").asLong();
        var beforeReset = login("session-test", "Original-Staff-Password");
        write(put("/api/admin/staff/" + id + "/password"), admin, Map.of("password", "Changed-Staff-Password"), 204);
        mvc.perform(get("/api/rooms").session(beforeReset.session())).andExpect(status().isUnauthorized());
        var afterReset = login("session-test", "Changed-Staff-Password");
        write(put("/api/admin/staff/" + id), admin, Map.of("displayName", "Session Test", "enabled", false), 204);
        mvc.perform(get("/api/rooms").session(afterReset.session())).andExpect(status().isUnauthorized());
    }
}
