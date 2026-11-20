package io.github.cpg25ncu.dormitory.housing;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.conflict;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import io.github.cpg25ncu.dormitory.common.CsvDownload;
import io.github.cpg25ncu.dormitory.common.Filters;
import io.github.cpg25ncu.dormitory.common.Page;
import io.github.cpg25ncu.dormitory.common.ResidenceTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Housing {
    private static final String MATCHING_DEPARTURES = """
            SELECT st.id, s.student_number, s.full_name, r.room_number, b.name AS building_name,
                st.check_in_at, st.check_out_at, st.departure_type, st.departure_reason,
                u.display_name AS processed_by
            FROM stay st JOIN student s ON s.id = st.student_id JOIN room r ON r.id = st.room_id
            JOIN building b ON b.id = r.building_id JOIN app_user u ON u.id = st.check_out_by
            WHERE st.check_out_at IS NOT NULL
                AND (:type IS NULL OR st.departure_type = :type)
                AND (:start IS NULL OR st.check_out_at >= :start)
                AND (:end IS NULL OR st.check_out_at < :end)
                AND (:pattern IS NULL OR LOWER(s.full_name) LIKE :pattern ESCAPE '!'
                    OR LOWER(s.student_number) LIKE :pattern ESCAPE '!' OR LOWER(st.departure_reason) LIKE :pattern ESCAPE '!')
            """;

    private final JdbcClient jdbc;
    private final Rooms rooms;
    private final AllocationRules rules;
    private final ResidenceTime time;

    Housing(JdbcClient jdbc, Rooms rooms, AllocationRules rules, ResidenceTime time) {
        this.jdbc = jdbc;
        this.rooms = rooms;
        this.rules = rules;
        this.time = time;
    }

    // Lock order for every housing write: student, rooms by ascending ID, then building. See AllocationRules.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void checkIn(long studentId, long roomId, long operator) {
        Gender gender = rules.lockStudent(studentId);
        if (currentStay(studentId).isPresent()) throw conflict("This student already has a room assignment.");
        ensureBedAvailable(roomId, rooms.lockCapacity(roomId));
        rules.ensureCanAssign(studentId, gender, roomId);
        openStay(studentId, roomId, operator, time.now());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transfer(long studentId, long targetRoom, long operator) {
        Gender gender = rules.lockStudent(studentId);
        var stay = currentStay(studentId).orElseThrow(() -> conflict("This student is not checked in."));
        long source = stay.roomId();
        if (source == targetRoom) throw conflict("Select a different room.");
        // All transfers acquire room locks in the same order.
        int first = rooms.lockCapacity(Math.min(source, targetRoom));
        int second = rooms.lockCapacity(Math.max(source, targetRoom));
        ensureBedAvailable(targetRoom, targetRoom < source ? first : second);
        rules.ensureCanAssign(studentId, gender, targetRoom);
        // The old stay ends at the same instant the new one starts.
        LocalDateTime now = time.now();
        closeStay(stay.id(), operator, "TRANSFER", "Room transfer", now);
        openStay(studentId, targetRoom, operator, now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void checkout(long studentId, String reason, long operator) {
        rules.lockStudent(studentId);
        var stay = currentStay(studentId).orElseThrow(() -> conflict("This student is not checked in."));
        rooms.lockCapacity(stay.roomId());
        closeStay(stay.id(), operator, "CHECKOUT", reason.strip(), time.now());
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

    public Page<Departure> history(HistoryFilter filter, Page.Request page) {
        long total = bind(jdbc.sql("SELECT COUNT(*) FROM (" + MATCHING_DEPARTURES + ") matches"), filter)
                .query(Long.class).single();
        return page.of(departures(filter, page), total);
    }

    /** Visits every matching departure, most recent first, reading a consistent snapshot in batches. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void exportHistory(HistoryFilter filter, Consumer<Departure> action) {
        CsvDownload.forEachRow(page -> departures(filter, page), action);
    }

    private List<Departure> departures(HistoryFilter filter, Page.Request page) {
        return bind(jdbc.sql(MATCHING_DEPARTURES + " ORDER BY st.check_out_at DESC, st.id DESC LIMIT :limit OFFSET :offset"), filter)
                .param("limit", page.size()).param("offset", page.offset())
                .query((row, number) -> new Departure(row.getLong("id"), row.getString("student_number"),
                        row.getString("full_name"), row.getString("room_number"), row.getString("building_name"),
                        ResidenceTime.instant(row.getObject("check_in_at", LocalDateTime.class)),
                        ResidenceTime.instant(row.getObject("check_out_at", LocalDateTime.class)),
                        row.getString("departure_type"), row.getString("departure_reason"), row.getString("processed_by")))
                .list();
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, HistoryFilter filter) {
        // Dates filter by the local day of the departure in the residence's time zone.
        return statement.param("pattern", filter.pattern(), Types.VARCHAR)
                .param("type", filter.type() == null ? null : filter.type().name(), Types.VARCHAR)
                .param("start", filter.from() == null ? null : time.startOf(filter.from()), Types.TIMESTAMP)
                .param("end", filter.to() == null ? null : time.startOf(filter.to().plusDays(1)), Types.TIMESTAMP);
    }

    private java.util.Optional<CurrentStay> currentStay(long student) {
        return jdbc.sql("SELECT id, room_id FROM stay WHERE active_student_id = ?")
                .param(student).query(CurrentStay.class).optional();
    }

    private void ensureBedAvailable(long room, int capacity) {
        if (rooms.occupied(room) >= capacity) throw conflict("The selected room has no available beds.");
    }

    private void openStay(long student, long room, long operator, LocalDateTime at) {
        jdbc.sql("""
                INSERT INTO stay (student_id, room_id, active_student_id, check_in_at, check_in_by)
                VALUES (?, ?, ?, ?, ?)
                """).params(student, room, student, at, operator).update();
    }

    private void closeStay(long stay, long operator, String type, String reason, LocalDateTime at) {
        jdbc.sql("""
                UPDATE stay SET active_student_id = NULL, check_out_at = ?,
                    check_out_by = ?, departure_type = ?, departure_reason = ? WHERE id = ?
                """).params(at, operator, type, reason, stay).update();
    }

    record CurrentStay(long id, long roomId) {}
    public record Assignment(@Positive long roomId) {}
    public record Checkout(@NotBlank @Size(max = 240) String reason) {}
    public record Resident(long stayId, long studentId, String studentNumber, String fullName,
                           long roomId, String roomNumber, long buildingId, String buildingName) {}
    public enum DepartureType { TRANSFER, CHECKOUT }

    /** Optional history filters. Dates are local departure days in the residence's time zone. */
    public record HistoryFilter(String pattern, DepartureType type, LocalDate from, LocalDate to) {
        public static HistoryFilter of(String q, String type, LocalDate from, LocalDate to) {
            Filters.ensureRange(from, to);
            return new HistoryFilter(Filters.contains(q), Filters.option(DepartureType.class, type, "type"), from, to);
        }
    }

    public record Departure(long id, String studentNumber, String fullName, String roomNumber,
                            String buildingName, Instant checkInAt, Instant checkOutAt, String departureType,
                            String departureReason, String processedBy) {}
}
