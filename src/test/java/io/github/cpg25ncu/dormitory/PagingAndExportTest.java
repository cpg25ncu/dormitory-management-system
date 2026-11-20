package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/** Paged search, filters and CSV exports for students, absences and housing history. */
class PagingAndExportTest extends ApiIntegrationTest {
    @Test
    void studentSearchPagesResultsAndFiltersByStatusAndGender() throws Exception {
        var admin = admin();
        for (int index = 1; index <= 25; index++) {
            write(post("/api/admin/students"), admin, studentInput("PAGE-%02d".formatted(index), index % 2 == 0 ? "MALE" : "FEMALE"), 201);
        }

        JsonNode first = page(admin, "/api/admin/students", "q", "page-", "size", "10");
        assertThat(first.get("totalItems").asLong()).isEqualTo(25);
        assertThat(first.get("totalPages").asInt()).isEqualTo(3);
        assertThat(numbers(first)).containsExactly("PAGE-01", "PAGE-02", "PAGE-03", "PAGE-04", "PAGE-05",
                "PAGE-06", "PAGE-07", "PAGE-08", "PAGE-09", "PAGE-10");
        assertThat(numbers(page(admin, "/api/admin/students", "q", "PAGE-", "size", "10", "page", "2")))
                .containsExactly("PAGE-21", "PAGE-22", "PAGE-23", "PAGE-24", "PAGE-25");
        JsonNode beyond = page(admin, "/api/admin/students", "q", "PAGE-", "size", "10", "page", "3");
        assertThat(beyond.get("items").isEmpty()).isTrue();
        assertThat(beyond.get("totalItems").asLong()).isEqualTo(25);

        assertThat(total(admin, "/api/admin/students", "q", "PAGE-", "gender", "male")).isEqualTo(12);
        assertThat(total(admin, "/api/admin/students", "q", "PAGE-", "status", "unassigned")).isEqualTo(25);
        assertThat(total(admin, "/api/admin/students", "q", "PAGE-", "status", "resident")).isZero();
    }

    @Test
    void searchTreatsWildcardCharactersLiterallyAndRejectsBadParameters() throws Exception {
        var admin = admin();
        for (String number : List.of("WILD-100%", "WILD-1000", "WILD_A", "WILDXA")) {
            write(post("/api/admin/students"), admin, studentInput(number), 201);
        }
        assertThat(numbers(page(admin, "/api/admin/students", "q", "100%"))).containsExactly("WILD-100%");
        assertThat(numbers(page(admin, "/api/admin/students", "q", "WILD_"))).containsExactly("WILD_A");

        for (String[] invalid : new String[][]{{"size", "0"}, {"size", "101"}, {"page", "-1"}, {"gender", "other"}, {"q", "x".repeat(101)}}) {
            mvc.perform(get("/api/admin/students").param(invalid[0], invalid[1]).session(admin.session()))
                    .andExpect(status().isBadRequest());
        }
        assertThat(problem(admin, "/api/admin/students", "status", "moved"))
                .isEqualTo("status must be one of: resident, unassigned, checked_out.");
    }

    @Test
    void absenceFiltersCombineWithTheStaffAccountsScope() throws Exception {
        var admin = admin();
        var staff = staff();
        long hall = building(admin, "Filter hall");
        long room = room(admin, hall, "F-01", 2);
        long student = student(admin, "FILTER-1");
        clock.fix(Instant.parse("2026-06-01T02:00:00Z"), RESIDENCE_ZONE);
        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", room), 201);
        long stay = activeStay(admin, "FILTER-1");
        clock.fix(Instant.parse("2026-06-10T02:00:00Z"), RESIDENCE_ZONE);
        for (String date : List.of("2026-06-02", "2026-06-05", "2026-06-09")) {
            write(post("/api/absences"), admin, Map.of("stayId", stay, "absenceDate", date, "reason", "Filter " + date), 201);
        }

        JsonNode range = page(admin, "/api/absences", "q", "FILTER-1", "from", "2026-06-03", "to", "2026-06-09");
        assertThat(range.get("items").findValuesAsString("absenceDate")).containsExactly("2026-06-09", "2026-06-05");
        assertThat(total(admin, "/api/absences", "buildingId", Long.toString(hall))).isEqualTo(3);
        assertThat(total(admin, "/api/absences", "q", "filter 2026-06-0")).isEqualTo(3);
        assertThat(total(staff, "/api/absences", "q", "FILTER-1")).isZero();
        assertThat(total(staff, "/api/absences", "buildingId", Long.toString(hall))).isZero();
        assertThat(problem(admin, "/api/absences", "from", "2026-06-09", "to", "2026-06-01"))
                .isEqualTo("from must be on or before to.");
        mvc.perform(get("/api/absences").param("from", "June 1").session(admin.session())).andExpect(status().isBadRequest());
    }

    @Test
    void historyFiltersByTypeAndTheLocalDayOfDeparture() throws Exception {
        var admin = admin();
        long hall = building(admin, "History filter hall");
        long first = room(admin, hall, "H-01", 1);
        long second = room(admin, hall, "H-02", 1);
        long student = student(admin, "HISTORY-1");
        clock.fix(Instant.parse("2026-07-01T02:00:00Z"), RESIDENCE_ZONE);
        write(post(studentOperation(student, "check-in")), admin, Map.of("roomId", first), 201);
        // 01:30 on 4 July in Shanghai is still 3 July in UTC.
        clock.fix(Instant.parse("2026-07-03T17:30:00Z"), RESIDENCE_ZONE);
        write(post(studentOperation(student, "transfer")), admin, Map.of("roomId", second), 204);
        clock.fix(Instant.parse("2026-07-05T02:00:00Z"), RESIDENCE_ZONE);
        write(post(studentOperation(student, "checkout")), admin, Map.of("reason", "End of term"), 204);

        assertThat(total(admin, "/api/admin/housing/history", "q", "HISTORY-1")).isEqualTo(2);
        assertThat(total(admin, "/api/admin/housing/history", "q", "HISTORY-1", "type", "transfer")).isOne();
        JsonNode localDay = page(admin, "/api/admin/housing/history", "q", "HISTORY-1", "from", "2026-07-04", "to", "2026-07-04");
        assertThat(localDay.get("items").findValuesAsString("departureType")).containsExactly("TRANSFER");
        assertThat(total(admin, "/api/admin/housing/history", "q", "HISTORY-1", "from", "2026-07-03", "to", "2026-07-03")).isZero();
        assertThat(problem(admin, "/api/admin/housing/history", "type", "moved"))
                .isEqualTo("type must be one of: transfer, checkout.");
    }

    @Test
    void exportsAreCsvAttachmentsThatNeutraliseFormulas() throws Exception {
        var admin = admin();
        var staff = staff();
        write(post("/api/admin/students"), admin, Map.of("studentNumber", "EXPORT-1", "gender", "FEMALE",
                "fullName", "=HYPERLINK(\"http://example.org\",\"Open\")", "program", "Data, Science"), 201);

        MockHttpServletResponse students = download(admin, "/api/admin/students/export.csv", "q", "EXPORT-1");
        assertThat(students.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(students.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"students-" + today() + ".csv\"");
        byte[] bytes = students.getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("﻿"
                + "Student number,Full name,Gender,Program,Phone,Email,Housing status,Building,Room\r\n"
                + "EXPORT-1,\"'=HYPERLINK(\"\"http://example.org\"\",\"\"Open\"\")\",Female,\"Data, Science\",,,Unassigned,,\r\n");

        String staffAbsences = download(staff, "/api/absences/export.csv").getContentAsString(StandardCharsets.UTF_8);
        assertThat(staffAbsences).startsWith("﻿Date,Student number,Full name,Building,Room,Reason,Status,")
                .contains("Cedar Hall").doesNotContain("Maple Hall");
        assertThat(download(admin, "/api/admin/housing/history/export.csv").getContentAsString(StandardCharsets.UTF_8))
                .startsWith("﻿Student number,Full name,Building,Room,Checked in,Closed,Type,Reason,Processed by\r\n");

        mvc.perform(get("/api/admin/students/export.csv").session(staff.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/absences/export.csv").param("status", "archived").session(staff.session()))
                .andExpect(status().isBadRequest());
    }

    private JsonNode page(Login login, String path, String... params) throws Exception {
        var request = get(path).session(login.session());
        for (int index = 0; index < params.length; index += 2) request.param(params[index], params[index + 1]);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long total(Login login, String path, String... params) throws Exception {
        return page(login, path, params).get("totalItems").asLong();
    }

    private String problem(Login login, String path, String... params) throws Exception {
        var request = get(path).session(login.session());
        for (int index = 0; index < params.length; index += 2) request.param(params[index], params[index + 1]);
        return json.readTree(mvc.perform(request).andExpect(status().isBadRequest()).andReturn().getResponse()
                .getContentAsString()).get("detail").asString();
    }

    private MockHttpServletResponse download(Login login, String path, String... params) throws Exception {
        var request = get(path).session(login.session());
        for (int index = 0; index < params.length; index += 2) request.param(params[index], params[index + 1]);
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
    }

    private static List<String> numbers(JsonNode page) {
        List<String> numbers = new ArrayList<>();
        for (JsonNode student : page.get("items")) numbers.add(student.get("studentNumber").asString());
        return numbers;
    }
}
