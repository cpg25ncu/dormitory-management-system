package io.github.cpg25ncu.dormitory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** Password changes, staff accounts, and the rules for editing and deleting rooms, buildings and students. */
class AccountAndDirectoryTest extends ApiIntegrationTest {
    @Test
    void changingAPasswordChecksTheCurrentOneAndEndsTheSession() throws Exception {
        write(post("/api/admin/staff"), admin(), Map.of("username", "password-owner", "displayName", "Password Owner",
                "password", "Original-Password-1"), 201);
        var staff = login("password-owner", "Original-Password-1");

        assertThat(rejected(put("/api/password"), staff, change("Not-The-Password", "Replacement-Password-1"), 400))
                .isEqualTo("Current password is incorrect.");
        assertThat(rejected(put("/api/password"), staff, change("Original-Password-1", "Original-Password-1"), 400))
                .isEqualTo("Choose a different password.");
        assertThat(rejected(put("/api/password"), staff, change("Original-Password-1", "too-short"), 400))
                .isEqualTo("newPassword size must be between 12 and 72");
        // Thirty three-byte characters pass the length check but not the 72-byte BCrypt limit.
        assertThat(rejected(put("/api/password"), staff, change("Original-Password-1", "密".repeat(30)), 400))
                .isEqualTo("Password must contain at least 12 characters and fit within 72 UTF-8 bytes.");

        write(put("/api/password"), staff, change("Original-Password-1", "Replacement-Password-1"), 204);
        mvc.perform(get("/api/rooms").session(staff.session())).andExpect(status().isUnauthorized());
        assertThat(signInStatus("password-owner", "Original-Password-1")).isEqualTo(401);
        login("password-owner", "Replacement-Password-1");
    }

    @Test
    void staffAccountsValidateUsernamesPasswordsAndTargets() throws Exception {
        var admin = admin();
        write(post("/api/admin/staff"), admin, staffInput("x", "Valid-Password-1"), 400);
        write(post("/api/admin/staff"), admin, staffInput("valid.name", "short"), 400);
        write(post("/api/admin/staff"), admin, staffInput("Duplicate.Name", "Valid-Password-1"), 201);
        // Usernames are stored in lower case, so a different case is still a duplicate.
        write(post("/api/admin/staff"), admin, staffInput("duplicate.name", "Valid-Password-1"), 409);
        assertThat(signInStatus("DUPLICATE.NAME", "Valid-Password-1")).isEqualTo(204);

        write(put("/api/admin/staff/999999"), admin, Map.of("displayName", "Missing", "enabled", true), 404);
        write(put("/api/admin/staff/999999/password"), admin, Map.of("password", "Valid-Password-2"), 404);
        // Staff cannot use the administration endpoints, even on their own account.
        long ownAccount = find(getJson("/api/admin/staff", admin), "username", "staff").get("id").asLong();
        write(put("/api/admin/staff/" + ownAccount), staff(), Map.of("displayName", "Escalation", "enabled", true), 403);
    }

    @Test
    void roomEditsKeepCapacityAboveOccupancyAndRoomsWithHistoryStay() throws Exception {
        var admin = admin();
        long hall = building(admin, "Room edit hall");
        long shared = room(admin, hall, "RE-01", 2);
        long unused = room(admin, hall, "RE-02", 2);
        for (String number : new String[]{"ROOM-EDIT-1", "ROOM-EDIT-2"}) {
            write(post(studentOperation(student(admin, number), "check-in")), admin, Map.of("roomId", shared), 201);
        }

        assertThat(rejected(put("/api/admin/rooms/" + shared), admin, roomInput("RE-01", 1), 409))
                .isEqualTo("Capacity cannot be less than the number of residents.");
        write(put("/api/admin/rooms/" + shared), admin, roomInput("RE-02", 3), 409);
        write(put("/api/admin/rooms/" + shared), admin, roomInput("RE-01A", 3), 204);
        assertThat(find(getJson("/api/rooms", admin), "id", Long.toString(shared)).get("roomNumber").asString())
                .isEqualTo("RE-01A");
        write(put("/api/admin/rooms/999999"), admin, roomInput("RE-99", 2), 404);
        write(put("/api/admin/rooms/" + unused), admin, roomInput("RE-02", 31), 400);

        write(delete("/api/admin/rooms/" + shared), admin, null, 409);
        write(delete("/api/admin/rooms/" + unused), admin, null, 204);
        write(delete("/api/admin/rooms/" + unused), admin, null, 404);
        write(post("/api/admin/rooms"), admin, Map.of("buildingId", 999999, "roomNumber", "X-1", "capacity", 1), 404);
    }

    @Test
    void buildingsAndStudentsCanOnlyBeDeletedWithoutLinkedRecords() throws Exception {
        var admin = admin();
        long withRoom = building(admin, "Delete check hall");
        room(admin, withRoom, "D-01", 1);
        long empty = building(admin, "Empty hall");
        write(delete("/api/admin/buildings/" + withRoom), admin, null, 409);
        write(delete("/api/admin/buildings/" + empty), admin, null, 204);
        write(delete("/api/admin/buildings/" + empty), admin, null, 404);
        write(put("/api/admin/buildings/999999"), admin, Map.of("name", "Missing hall"), 404);

        long unassigned = student(admin, "DELETE-1");
        write(delete("/api/admin/students/" + unassigned), admin, null, 204);
        write(delete("/api/admin/students/" + unassigned), admin, null, 404);
        write(put("/api/admin/students/999999"), admin, studentInput("DELETE-2"), 404);
        write(post("/api/admin/students"), admin, studentInput("DEMO-001"), 409);
    }

    @Test
    void onlyEnabledStaffCanBeAssignedToNewBuildings() throws Exception {
        var admin = admin();
        write(post("/api/admin/staff"), admin, staffInput("retired.staff", "Valid-Password-1"), 201);
        long retired = find(getJson("/api/admin/staff", admin), "username", "retired.staff").get("id").asLong();
        long hall = building(admin, "Managed hall");
        write(put("/api/admin/buildings/" + hall), admin, Map.of("name", "Managed hall", "managerId", retired), 204);
        write(put("/api/admin/staff/" + retired), admin, Map.of("displayName", "Retired Staff", "enabled", false), 204);

        // A disabled manager can stay on a building but cannot be given a new one.
        write(put("/api/admin/buildings/" + hall), admin, Map.of("name", "Managed hall", "managerId", retired), 204);
        assertThat(rejected(post("/api/admin/buildings"), admin, Map.of("name", "Second hall", "managerId", retired), 400))
                .isEqualTo("Select an enabled dormitory staff account.");
        // The bootstrap administrator is the first account. Administrators cannot manage a building.
        long administrator = 1;
        write(post("/api/admin/buildings"), admin, Map.of("name", "Admin hall", "managerId", administrator), 400);
    }

    private int signInStatus(String username, String password) throws Exception {
        var initial = mvc.perform(get("/api/session")).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        var csrf = json.readTree(initial.getResponse().getContentAsString());
        return mvc.perform(post("/api/login").session(session)
                        .header(csrf.get("csrfHeader").asString(), csrf.get("csrfToken").asString())
                        .param("username", username).param("password", password))
                .andReturn().getResponse().getStatus();
    }

    private static Map<String, String> change(String current, String replacement) {
        return Map.of("currentPassword", current, "newPassword", replacement);
    }

    private static Map<String, String> staffInput(String username, String password) {
        return Map.of("username", username, "displayName", "Staff " + username, "password", password);
    }

    private static Map<String, Object> roomInput(String number, int capacity) {
        return Map.of("roomNumber", number, "capacity", capacity);
    }
}
