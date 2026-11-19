package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ResidenceWorkflowsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void mysqlWhenConfigured(DynamicPropertyRegistry registry) {
        String url = System.getenv("DORM_TEST_DB_URL");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getenv("DORM_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DORM_TEST_DB_PASSWORD"));
    }

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
        var staff = login("staff", "DemoStaff-Only-2026");
        mvc.perform(get("/api/admin/students").session(staff.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/buildings").session(staff.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Cedar Hall"));
        assertThat(getJson("/api/rooms", staff).size()).isEqualTo(3);
        assertThat(getJson("/api/absences", staff).toString()).doesNotContain("Maple Hall");

        long assignedStay = find(getJson("/api/residents", staff), "studentNumber", "DEMO-002").get("stayId").asLong();
        long otherStay = find(getJson("/api/residents", admin), "studentNumber", "DEMO-006").get("stayId").asLong();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        write(post("/api/absences"), staff, Map.of("stayId", otherStay, "absenceDate", today, "reason", "No access"), 404);
        write(post("/api/absences"), staff, Map.of("stayId", assignedStay, "absenceDate", today, "reason", "Approved leave"), 201);
        write(post("/api/absences"), staff, Map.of("stayId", assignedStay, "absenceDate", today, "reason", "Duplicate entry"), 409);
        assertThat(getJson("/api/absences", admin).toString()).contains("Approved leave");
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
        long activeStay = find(getJson("/api/residents", admin), "studentNumber", "TRANSFER-1").get("stayId").asLong();
        write(post("/api/absences"), admin, Map.of("stayId", activeStay, "reason", "First-day absence",
                "absenceDate", LocalDate.now(ZoneOffset.UTC).toString()), 201);
        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", destination), 409);
        write(post(studentOperation(other, "check-in")), admin, Map.of("roomId", destination), 201);
        write(post(studentOperation(student, "transfer")), admin, Map.of("roomId", destination), 409);
        assertThat(find(getJson("/api/admin/students", admin), "studentNumber", "TRANSFER-1").get("roomId").asLong())
                .isEqualTo(originalRoom);

        write(post(studentOperation(other, "checkout")), admin, Map.of("reason", "Room change"), 204);
        write(post(studentOperation(student, "transfer")), admin, Map.of("roomId", destination), 204);
        assertThat(occupied(admin, originalRoom)).isZero();
        assertThat(occupied(admin, destination)).isOne();
        write(post(studentOperation(student, "checkout")), admin, Map.of("reason", "End of residence"), 204);
        write(post(studentOperation(student, "checkout")), admin, Map.of("reason", "Repeated checkout"), 409);
        write(delete("/api/admin/students/" + student), admin, null, 409);

        JsonNode history = getJson("/api/admin/housing/history", admin);
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

    private Login admin() throws Exception {
        return login("admin", "DemoAdmin-Only-2026");
    }

    private Login login(String username, String password) throws Exception {
        var initial = mvc.perform(get("/api/session")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        var csrf = json.readTree(initial.getResponse().getContentAsString());
        var signedIn = mvc.perform(post("/api/login").session(session)
                        .header(csrf.get("csrfHeader").asText(), csrf.get("csrfToken").asText())
                        .param("username", username).param("password", password))
                .andExpect(status().isNoContent()).andReturn();
        var signedInSession = (MockHttpSession) signedIn.getRequest().getSession(false);
        var state = mvc.perform(get("/api/session").session(signedInSession)).andReturn();
        var payload = json.readTree(state.getResponse().getContentAsString());
        assertThat(payload.get("user").get("username").asText()).isEqualTo(username);
        return new Login(signedInSession, payload.get("csrfHeader").asText(), payload.get("csrfToken").asText());
    }

    private long building(Login admin, String name) throws Exception {
        write(post("/api/admin/buildings"), admin, Map.of("name", name), 201);
        return find(getJson("/api/buildings", admin), "name", name).get("id").asLong();
    }

    private long room(Login admin, long building, String number, int capacity) throws Exception {
        write(post("/api/admin/rooms"), admin, Map.of("buildingId", building, "roomNumber", number, "capacity", capacity), 201);
        return find(getJson("/api/rooms", admin), "roomNumber", number).get("id").asLong();
    }

    private long student(Login admin, String number) throws Exception {
        write(post("/api/admin/students"), admin, studentInput(number), 201);
        return find(getJson("/api/admin/students", admin), "studentNumber", number).get("id").asLong();
    }

    private int occupied(Login admin, long room) throws Exception {
        return find(getJson("/api/rooms", admin), "id", Long.toString(room)).get("occupied").asInt();
    }

    private JsonNode getJson(String path, Login login) throws Exception {
        String body = mvc.perform(get(path).session(login.session())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static JsonNode find(JsonNode rows, String key, String value) {
        for (Iterator<JsonNode> iterator = rows.elements(); iterator.hasNext(); ) {
            var row = iterator.next();
            if (value.equals(row.get(key).asText())) return row;
        }
        throw new AssertionError("Fixture not found: " + key + " = " + value);
    }

    private static String studentOperation(long student, String operation) {
        return "/api/admin/students/" + student + "/" + operation;
    }

    private static Map<String, String> studentInput(String number) {
        return Map.of("studentNumber", number, "fullName", "Test Resident " + number, "program", "Testing");
    }

    private void write(MockHttpServletRequestBuilder request, Login login, Object body, int expected) throws Exception {
        assertThat(perform(request, login, body)).isEqualTo(expected);
    }

    private int perform(MockHttpServletRequestBuilder request, Login login, Object body) throws Exception {
        request.session(login.session()).header(login.header(), login.token());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private record Login(MockHttpSession session, String header, String token) {}
}
