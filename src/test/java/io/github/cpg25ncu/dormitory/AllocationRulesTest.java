package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/** Building gender policies, single-gender rooms and the checks on later edits. */
class AllocationRulesTest extends ApiIntegrationTest {
    @Autowired JdbcClient jdbc;

    @Test
    void buildingPolicyAndRoommatesDecideWhoARoomAccepts() throws Exception {
        var admin = admin();
        long womensRoom = room(admin, building(admin, "Rules hall for women", "FEMALE_ONLY"), "W-01", 2);
        long mixedHall = building(admin, "Rules hall mixed", "MIXED");
        long shared = room(admin, mixedHall, "M-01", 3);
        long empty = room(admin, mixedHall, "M-02", 3);
        long man = student(admin, "RULES-M1", "MALE");
        long secondMan = student(admin, "RULES-M2", "MALE");
        long woman = student(admin, "RULES-F1", "FEMALE");

        assertThat(rejected(post(studentOperation(man, "check-in")), admin, Map.of("roomId", womensRoom), 409))
                .isEqualTo("The selected building houses female students only.");
        write(post(studentOperation(woman, "check-in")), admin, Map.of("roomId", shared), 201);
        assertThat(rejected(post(studentOperation(man, "check-in")), admin, Map.of("roomId", shared), 409))
                .isEqualTo("The selected room has residents who are not recorded as male.");
        write(post(studentOperation(man, "check-in")), admin, Map.of("roomId", empty), 201);
        write(post(studentOperation(secondMan, "check-in")), admin, Map.of("roomId", empty), 201);

        assertThat(rejected(post(studentOperation(man, "transfer")), admin, Map.of("roomId", womensRoom), 409))
                .isEqualTo("The selected building houses female students only.");
        assertThat(rejected(post(studentOperation(man, "transfer")), admin, Map.of("roomId", shared), 409))
                .isEqualTo("The selected room has residents who are not recorded as male.");
        assertThat(studentRecord(admin, "RULES-M1").get("roomId").asLong())
                .isEqualTo(empty);

        JsonNode rooms = getJson("/api/rooms", admin);
        assertThat(find(rooms, "id", Long.toString(shared)).get("residentGender").asString()).isEqualTo("FEMALE");
        assertThat(find(rooms, "id", Long.toString(empty)).get("residentGender").asString()).isEqualTo("MALE");
        assertThat(find(rooms, "id", Long.toString(womensRoom)).get("residentGender").isNull()).isTrue();
        assertThat(find(rooms, "id", Long.toString(womensRoom)).get("genderPolicy").asString()).isEqualTo("FEMALE_ONLY");
    }

    @Test
    void studentsWithoutARecordedGenderCannotBeAssigned() throws Exception {
        var admin = admin();
        long room = room(admin, building(admin, "Legacy record hall"), "L-01", 2);
        // Students created before version 2 of the schema have no recorded gender.
        jdbc.sql("INSERT INTO student (student_number, full_name, program) VALUES ('LEGACY-1', 'Legacy Resident', '')")
                .update();
        JsonNode legacy = studentRecord(admin, "LEGACY-1");
        assertThat(legacy.get("gender").isNull()).isTrue();
        long id = legacy.get("id").asLong();

        assertThat(rejected(post(studentOperation(id, "check-in")), admin, Map.of("roomId", room), 409))
                .isEqualTo("Record this student's gender before assigning a room.");
        write(put("/api/admin/students/" + id), admin, Map.of("studentNumber", "LEGACY-1",
                "fullName", "Legacy Resident", "gender", "MALE"), 204);
        write(post(studentOperation(id, "check-in")), admin, Map.of("roomId", room), 201);
    }

    @Test
    void policyAndGenderEditsMustFitCurrentResidents() throws Exception {
        var admin = admin();
        long hall = building(admin, "Edit check hall");
        long room = room(admin, hall, "E-01", 2);
        long first = student(admin, "EDIT-M1", "MALE");
        long second = student(admin, "EDIT-M2", "MALE");
        long unassigned = student(admin, "EDIT-M3", "MALE");
        write(post(studentOperation(first, "check-in")), admin, Map.of("roomId", room), 201);
        write(post(studentOperation(second, "check-in")), admin, Map.of("roomId", room), 201);

        assertThat(rejected(put("/api/admin/students/" + first), admin, studentInput("EDIT-M1", "FEMALE"), 409))
                .isEqualTo("This gender does not match the student's current building or roommates. Transfer the student first.");
        write(put("/api/admin/students/" + unassigned), admin, studentInput("EDIT-M3", "FEMALE"), 204);

        assertThat(rejected(put("/api/admin/buildings/" + hall), admin, buildingInput("Edit check hall", "FEMALE_ONLY"), 409))
                .isEqualTo("This building has residents who are not recorded as female.");
        write(put("/api/admin/buildings/" + hall), admin, buildingInput("Edit check hall", "MALE_ONLY"), 204);
        assertThat(find(getJson("/api/buildings", admin), "name", "Edit check hall").get("genderPolicy").asString())
                .isEqualTo("MALE_ONLY");

        write(post(studentOperation(second, "checkout")), admin, Map.of("reason", "Moved out"), 204);
        write(post(studentOperation(first, "checkout")), admin, Map.of("reason", "Moved out"), 204);
        write(put("/api/admin/buildings/" + hall), admin, buildingInput("Edit check hall", "FEMALE_ONLY"), 204);
    }

    @Test
    void aPolicyChangeAndACheckInAtTheSameTimeCannotBothSucceed() throws Exception {
        var admin = admin();
        var threads = Executors.newFixedThreadPool(2);
        try {
            for (int round = 1; round <= 5; round++) {
                String name = "Race hall " + round;
                long hall = building(admin, name);
                long room = room(admin, hall, "R-01", 2);
                long woman = student(admin, "RACE-F" + round, "FEMALE");
                var start = new CountDownLatch(1);
                var policyChange = threads.submit(() -> {
                    start.await();
                    return perform(put("/api/admin/buildings/" + hall), admin, buildingInput(name, "MALE_ONLY"));
                });
                var checkIn = threads.submit(() -> {
                    start.await();
                    return perform(post(studentOperation(woman, "check-in")), admin, Map.of("roomId", room));
                });
                start.countDown();
                int policyStatus = policyChange.get(10, TimeUnit.SECONDS);
                int checkInStatus = checkIn.get(10, TimeUnit.SECONDS);

                // Whichever request locks the building first wins, and the other sees its result.
                assertThat(policyStatus == 204 ? checkInStatus : policyStatus).isEqualTo(409);
                assertThat(policyStatus == 204 || checkInStatus == 201).isTrue();
                boolean restricted = "MALE_ONLY".equals(find(getJson("/api/buildings", admin), "name", name)
                        .get("genderPolicy").asString());
                assertThat(restricted).isNotEqualTo(occupied(admin, room) == 1);
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void studentRecordsRequireAGenderAndWellFormedContactDetails() throws Exception {
        var admin = admin();
        assertThat(rejected(post("/api/admin/students"), admin,
                Map.of("studentNumber", "INPUT-1", "fullName", "Input Check"), 400))
                .isEqualTo("gender must not be null");
        write(post("/api/admin/students"), admin, withContact("INPUT-2", "+86 138-0000-0000", "not-an-email"), 400);
        write(post("/api/admin/students"), admin, withContact("INPUT-3", "call me", "resident@example.org"), 400);
        var unknownGender = new HashMap<>(studentInput("INPUT-4"));
        unknownGender.put("gender", "UNKNOWN");
        write(post("/api/admin/students"), admin, unknownGender, 400);

        write(post("/api/admin/students"), admin, withContact("INPUT-5", "+86 138-0000-0000", "resident@example.org"), 201);
        JsonNode created = studentRecord(admin, "INPUT-5");
        assertThat(created.get("phone").asString()).isEqualTo("+86 138-0000-0000");
        assertThat(created.get("email").asString()).isEqualTo("resident@example.org");
        assertThat(created.get("gender").asString()).isEqualTo("FEMALE");
    }

    private static Map<String, Object> buildingInput(String name, String policy) {
        return Map.of("name", name, "genderPolicy", policy);
    }

    private static Map<String, String> withContact(String number, String phone, String email) {
        var input = new HashMap<>(studentInput(number));
        input.put("phone", phone);
        input.put("email", email);
        return input;
    }
}
