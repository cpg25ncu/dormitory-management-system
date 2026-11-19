package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared setup for HTTP integration tests. The suite runs against the demo profile on H2, or on MySQL
 * when {@code DORM_TEST_DB_URL} is set. All subclasses share one application context and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(ApiIntegrationTest.TestClockConfiguration.class)
abstract class ApiIntegrationTest {
    /** A zone ahead of UTC, so code that silently uses UTC dates fails for part of each day. */
    static final ZoneId RESIDENCE_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;

    @DynamicPropertySource
    static void mysqlWhenConfigured(DynamicPropertyRegistry registry) {
        String url = System.getenv("DORM_TEST_DB_URL");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getenv("DORM_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DORM_TEST_DB_PASSWORD"));
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    JsonNode session() throws Exception {
        return json.readTree(mvc.perform(get("/api/session")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    Login admin() throws Exception {
        return demoLogin("ADMIN");
    }

    Login staff() throws Exception {
        return demoLogin("STAFF");
    }

    private Login demoLogin(String role) throws Exception {
        for (JsonNode account : session().get("demoAccounts")) {
            if (role.equals(account.get("role").asString())) {
                return login(account.get("username").asString(), account.get("password").asString());
            }
        }
        throw new AssertionError("The demo profile offers no " + role + " account.");
    }

    Login login(String username, String password) throws Exception {
        var initial = mvc.perform(get("/api/session")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        var csrf = json.readTree(initial.getResponse().getContentAsString());
        var signedIn = mvc.perform(post("/api/login").session(session)
                        .header(csrf.get("csrfHeader").asString(), csrf.get("csrfToken").asString())
                        .param("username", username).param("password", password))
                .andExpect(status().isNoContent()).andReturn();
        var signedInSession = (MockHttpSession) signedIn.getRequest().getSession(false);
        var state = mvc.perform(get("/api/session").session(signedInSession)).andReturn();
        var payload = json.readTree(state.getResponse().getContentAsString());
        assertThat(payload.get("user").get("username").asString()).isEqualTo(username);
        return new Login(signedInSession, payload.get("csrfHeader").asString(), payload.get("csrfToken").asString());
    }

    long building(Login admin, String name) throws Exception {
        return building(admin, name, "MIXED");
    }

    long building(Login admin, String name, String genderPolicy) throws Exception {
        write(post("/api/admin/buildings"), admin, Map.of("name", name, "genderPolicy", genderPolicy), 201);
        return find(getJson("/api/buildings", admin), "name", name).get("id").asLong();
    }

    long room(Login admin, long building, String number, int capacity) throws Exception {
        write(post("/api/admin/rooms"), admin, Map.of("buildingId", building, "roomNumber", number, "capacity", capacity), 201);
        for (JsonNode room : getJson("/api/rooms", admin)) {
            if (room.get("buildingId").asLong() == building && number.equals(room.get("roomNumber").asString())) {
                return room.get("id").asLong();
            }
        }
        throw new AssertionError("Room not found: " + number);
    }

    long student(Login admin, String number) throws Exception {
        return student(admin, number, "FEMALE");
    }

    long student(Login admin, String number, String gender) throws Exception {
        write(post("/api/admin/students"), admin, studentInput(number, gender), 201);
        return find(getJson("/api/admin/students", admin), "studentNumber", number).get("id").asLong();
    }

    long activeStay(Login login, String studentNumber) throws Exception {
        return find(getJson("/api/residents", login), "studentNumber", studentNumber).get("stayId").asLong();
    }

    int occupied(Login admin, long room) throws Exception {
        return find(getJson("/api/rooms", admin), "id", Long.toString(room)).get("occupied").asInt();
    }

    JsonNode getJson(String path, Login login) throws Exception {
        String body = mvc.perform(get(path).session(login.session())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    static JsonNode find(JsonNode rows, String key, String value) {
        for (JsonNode row : rows) {
            if (value.equals(row.get(key).asString())) return row;
        }
        throw new AssertionError("Fixture not found: " + key + " = " + value);
    }

    static String studentOperation(long student, String operation) {
        return "/api/admin/students/" + student + "/" + operation;
    }

    static Map<String, String> studentInput(String number) {
        return studentInput(number, "FEMALE");
    }

    static Map<String, String> studentInput(String number, String gender) {
        return Map.of("studentNumber", number, "fullName", "Test Resident " + number, "gender", gender, "program", "Testing");
    }

    void write(MockHttpServletRequestBuilder request, Login login, Object body, int expected) throws Exception {
        assertThat(perform(request, login, body)).isEqualTo(expected);
    }

    int perform(MockHttpServletRequestBuilder request, Login login, Object body) throws Exception {
        return send(request, login, body).getStatus();
    }

    /** Sends a write that should fail and returns the problem detail message. */
    String rejected(MockHttpServletRequestBuilder request, Login login, Object body, int expected) throws Exception {
        var response = send(request, login, body);
        assertThat(response.getStatus()).isEqualTo(expected);
        return json.readTree(response.getContentAsString()).get("detail").asString();
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, Login login, Object body) throws Exception {
        request.session(login.session()).header(login.header(), login.token());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request).andReturn().getResponse();
    }

    record Login(MockHttpSession session, String header, String token) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(RESIDENCE_ZONE);
        }
    }
}
