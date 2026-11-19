package io.github.cpg25ncu.dormitory.housing;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.conflict;
import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Housing {
    private final JdbcClient jdbc;
    private final Rooms rooms;

    Housing(JdbcClient jdbc, Rooms rooms) {
        this.jdbc = jdbc;
        this.rooms = rooms;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void checkIn(long studentId, long roomId, long operator) {
        lockStudent(studentId);
        if (currentStay(studentId).isPresent()) throw conflict("This student already has a room assignment.");
        ensureBedAvailable(roomId, rooms.lockCapacity(roomId));
        openStay(studentId, roomId, operator);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transfer(long studentId, long targetRoom, long operator) {
        lockStudent(studentId);
        var stay = currentStay(studentId).orElseThrow(() -> conflict("This student is not checked in."));
        long source = stay.roomId();
        if (source == targetRoom) throw conflict("Select a different room.");
        // All transfers acquire room locks in the same order.
        int first = rooms.lockCapacity(Math.min(source, targetRoom));
        int second = rooms.lockCapacity(Math.max(source, targetRoom));
        ensureBedAvailable(targetRoom, targetRoom < source ? first : second);
        closeStay(stay.id(), operator, "TRANSFER", "Room transfer");
        openStay(studentId, targetRoom, operator);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void checkout(long studentId, String reason, long operator) {
        lockStudent(studentId);
        var stay = currentStay(studentId).orElseThrow(() -> conflict("This student is not checked in."));
        rooms.lockCapacity(stay.roomId());
        closeStay(stay.id(), operator, "CHECKOUT", reason.strip());
    }

    public List<Resident> residents(AccountPrincipal account) {
        return jdbc.sql("""
                SELECT st.id AS stay_id, s.id AS student_id, s.student_number, s.full_name,
                    r.id AS room_id, r.room_number, b.id AS building_id, b.name AS building_name
                FROM stay st JOIN student s ON s.id = st.student_id JOIN room r ON r.id = st.room_id
                JOIN building b ON b.id = r.building_id
                WHERE st.check_out_at IS NULL AND (:admin = TRUE OR b.manager_id = :account)
                ORDER BY s.full_name, s.id
                """).param("admin", account.isAdmin()).param("account", account.id()).query(Resident.class).list();
    }

    public List<Departure> history() {
        return jdbc.sql("""
                SELECT st.id, s.student_number, s.full_name, r.room_number, b.name AS building_name,
                    st.check_in_at, st.check_out_at, st.departure_type, st.departure_reason,
                    u.display_name AS processed_by
                FROM stay st JOIN student s ON s.id = st.student_id JOIN room r ON r.id = st.room_id
                JOIN building b ON b.id = r.building_id JOIN app_user u ON u.id = st.check_out_by
                WHERE st.check_out_at IS NOT NULL ORDER BY st.check_out_at DESC, st.id DESC
                """).query((row, number) -> new Departure(row.getLong("id"), row.getString("student_number"),
                        row.getString("full_name"), row.getString("room_number"), row.getString("building_name"),
                        row.getObject("check_in_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        row.getObject("check_out_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        row.getString("departure_type"), row.getString("departure_reason"), row.getString("processed_by")))
                .list();
    }

    private void lockStudent(long id) {
        jdbc.sql("SELECT id FROM student WHERE id = ? FOR UPDATE")
                .param(id).query(Long.class).optional().orElseThrow(() -> notFound("Student"));
    }

    private java.util.Optional<CurrentStay> currentStay(long student) {
        return jdbc.sql("SELECT id, room_id FROM stay WHERE active_student_id = ?")
                .param(student).query(CurrentStay.class).optional();
    }

    private void ensureBedAvailable(long room, int capacity) {
        if (rooms.occupied(room) >= capacity) throw conflict("The selected room has no available beds.");
    }

    private void openStay(long student, long room, long operator) {
        jdbc.sql("INSERT INTO stay (student_id, room_id, active_student_id, check_in_by) VALUES (?, ?, ?, ?)")
                .params(student, room, student, operator).update();
    }

    private void closeStay(long stay, long operator, String type, String reason) {
        jdbc.sql("""
                UPDATE stay SET active_student_id = NULL, check_out_at = CURRENT_TIMESTAMP(6),
                    check_out_by = ?, departure_type = ?, departure_reason = ? WHERE id = ?
                """).params(operator, type, reason, stay).update();
    }

    record CurrentStay(long id, long roomId) {}
    public record Assignment(@Positive long roomId) {}
    public record Checkout(@NotBlank @Size(max = 240) String reason) {}
    public record Resident(long stayId, long studentId, String studentNumber, String fullName,
                           long roomId, String roomNumber, long buildingId, String buildingName) {}
    public record Departure(long id, String studentNumber, String fullName, String roomNumber,
                            String buildingName, Instant checkInAt, Instant checkOutAt, String departureType,
                            String departureReason, String processedBy) {}
}
