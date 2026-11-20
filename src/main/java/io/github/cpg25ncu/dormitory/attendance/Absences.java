package io.github.cpg25ncu.dormitory.attendance;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.conflict;
import static io.github.cpg25ncu.dormitory.common.ApiErrors.invalid;
import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import io.github.cpg25ncu.dormitory.common.CsvDownload;
import io.github.cpg25ncu.dormitory.common.Filters;
import io.github.cpg25ncu.dormitory.common.Page;
import io.github.cpg25ncu.dormitory.common.ResidenceTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Absence records for residents. Staff work only with the buildings assigned to them.
 *
 * <p>Every write locks the stay row first, so writes for one resident's stay run one at a time and
 * never race a checkout of that stay.
 */
@Service
public class Absences {
    private static final String MATCHING_ABSENCES = """
            SELECT a.id, a.stay_id, a.absence_date, a.reason, s.student_number, s.full_name,
                r.room_number, b.id AS building_id, b.name AS building_name, u.display_name AS recorded_by, a.created_at,
                editor.display_name AS updated_by, a.updated_at,
                withdrawer.display_name AS withdrawn_by, a.withdrawn_at, a.withdrawal_reason
            FROM absence_record a JOIN stay st ON st.id = a.stay_id JOIN student s ON s.id = st.student_id
            JOIN room r ON r.id = st.room_id JOIN building b ON b.id = r.building_id
            JOIN app_user u ON u.id = a.recorded_by
            LEFT JOIN app_user editor ON editor.id = a.updated_by
            LEFT JOIN app_user withdrawer ON withdrawer.id = a.withdrawn_by
            WHERE (:admin = TRUE OR b.manager_id = :account)
                AND (:status = 'ALL' OR (:status = 'ACTIVE' AND a.withdrawn_at IS NULL)
                    OR (:status = 'WITHDRAWN' AND a.withdrawn_at IS NOT NULL))
                AND (:building IS NULL OR b.id = :building)
                AND (:from IS NULL OR a.absence_date >= :from)
                AND (:to IS NULL OR a.absence_date <= :to)
                AND (:pattern IS NULL OR LOWER(s.full_name) LIKE :pattern ESCAPE '!'
                    OR LOWER(s.student_number) LIKE :pattern ESCAPE '!' OR LOWER(a.reason) LIKE :pattern ESCAPE '!')
            """;

    private final JdbcClient jdbc;
    private final ResidenceTime time;

    Absences(JdbcClient jdbc, ResidenceTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public Page<Absence> search(AccountPrincipal account, Filter filter, Page.Request page) {
        long total = bind(jdbc.sql("SELECT COUNT(*) FROM (" + MATCHING_ABSENCES + ") matches"), account, filter)
                .query(Long.class).single();
        return page.of(fetch(account, filter, page), total);
    }

    /** Visits every matching record, most recent first, reading a consistent snapshot in batches. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(AccountPrincipal account, Filter filter, Consumer<Absence> action) {
        CsvDownload.forEachRow(page -> fetch(account, filter, page), action);
    }

    private List<Absence> fetch(AccountPrincipal account, Filter filter, Page.Request page) {
        return bind(jdbc.sql(MATCHING_ABSENCES + " ORDER BY a.absence_date DESC, a.id DESC LIMIT :limit OFFSET :offset"),
                account, filter).param("limit", page.size()).param("offset", page.offset())
                .query((row, number) -> new Absence(row.getLong("id"), row.getLong("stay_id"),
                        row.getObject("absence_date", LocalDate.class), row.getString("reason"),
                        row.getString("student_number"), row.getString("full_name"), row.getString("room_number"),
                        row.getLong("building_id"), row.getString("building_name"), row.getString("recorded_by"),
                        instant(row.getObject("created_at", LocalDateTime.class)),
                        row.getString("updated_by"), instant(row.getObject("updated_at", LocalDateTime.class)),
                        row.getObject("withdrawn_at") == null ? "ACTIVE" : "WITHDRAWN",
                        row.getString("withdrawn_by"), instant(row.getObject("withdrawn_at", LocalDateTime.class)),
                        row.getString("withdrawal_reason")))
                .list();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, AccountPrincipal account, Filter filter) {
        return statement.param("admin", account.isAdmin()).param("account", account.id())
                .param("status", filter.status().name()).param("pattern", filter.pattern(), Types.VARCHAR)
                .param("building", filter.buildingId(), Types.BIGINT)
                .param("from", filter.from(), Types.DATE).param("to", filter.to(), Types.DATE);
    }

    /** Records an absence, or restores a withdrawn record for the same resident and day. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void record(NewAbsence input, AccountPrincipal account) {
        if (input.absenceDate().isAfter(time.today())) {
            throw invalid("Absence dates must be today or earlier.");
        }
        lockStay(input.stayId()).orElseThrow(() -> notFound("Active room assignment for that date"));
        // The stay must still be open, visible to this account, and have started by the end of the absence day.
        int eligible = jdbc.sql("""
                SELECT COUNT(*) FROM stay st JOIN room r ON r.id = st.room_id JOIN building b ON b.id = r.building_id
                WHERE st.id = :stay AND st.check_out_at IS NULL AND st.check_in_at < :dayEnd
                    AND (:admin = TRUE OR b.manager_id = :account)
                """).param("stay", input.stayId()).param("dayEnd", time.startOf(input.absenceDate().plusDays(1)))
                .param("admin", account.isAdmin()).param("account", account.id()).query(Integer.class).single();
        if (eligible == 0) throw notFound("Active room assignment for that date");

        LocalDateTime now = time.now();
        String reason = input.reason().strip();
        int restored = jdbc.sql("""
                UPDATE absence_record SET reason = :reason, recorded_by = :account, created_at = :now,
                    updated_at = NULL, updated_by = NULL, withdrawn_at = NULL, withdrawn_by = NULL, withdrawal_reason = NULL
                WHERE stay_id = :stay AND absence_date = :date AND withdrawn_at IS NOT NULL
                """).param("reason", reason).param("account", account.id()).param("now", now)
                .param("stay", input.stayId()).param("date", input.absenceDate()).update();
        if (restored > 0) return;
        if (recordExists(input.stayId(), input.absenceDate(), null)) throw duplicate();
        jdbc.sql("""
                INSERT INTO absence_record (stay_id, absence_date, reason, recorded_by, created_at)
                VALUES (?, ?, ?, ?, ?)
                """).params(input.stayId(), input.absenceDate(), reason, account.id(), now).update();
    }

    /** Corrects the date or reason of an active record. The date must fall within the stay. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void correct(long id, Correction input, AccountPrincipal account) {
        var stay = lockStay(visibleStayOf(id, account)).orElseThrow(() -> notFound("Absence record"));
        ensureActive(id, "Withdrawn records cannot be corrected. Record the absence again instead.");
        LocalDate first = time.dayOf(stay.checkInAt());
        LocalDate last = stay.checkOutAt() == null ? time.today() : min(time.dayOf(stay.checkOutAt()), time.today());
        if (input.absenceDate().isBefore(first) || input.absenceDate().isAfter(last)) {
            throw invalid("Choose a date during this stay, from " + first + " to " + last + ".");
        }
        if (recordExists(stay.id(), input.absenceDate(), id)) throw duplicate();
        jdbc.sql("""
                UPDATE absence_record SET absence_date = ?, reason = ?, updated_at = ?, updated_by = ? WHERE id = ?
                """).params(input.absenceDate(), input.reason().strip(), time.now(), account.id(), id).update();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(long id, Withdrawal input, AccountPrincipal account) {
        lockStay(visibleStayOf(id, account)).orElseThrow(() -> notFound("Absence record"));
        ensureActive(id, "This absence record is already withdrawn.");
        jdbc.sql("""
                UPDATE absence_record SET withdrawn_at = ?, withdrawn_by = ?, withdrawal_reason = ? WHERE id = ?
                """).params(time.now(), account.id(), input.reason().strip(), id).update();
    }

    private long visibleStayOf(long absenceId, AccountPrincipal account) {
        return jdbc.sql("""
                SELECT a.stay_id FROM absence_record a JOIN stay st ON st.id = a.stay_id
                JOIN room r ON r.id = st.room_id JOIN building b ON b.id = r.building_id
                WHERE a.id = :id AND (:admin = TRUE OR b.manager_id = :account)
                """).param("id", absenceId).param("admin", account.isAdmin()).param("account", account.id())
                .query(Long.class).optional().orElseThrow(() -> notFound("Absence record"));
    }

    private java.util.Optional<Stay> lockStay(long stayId) {
        return jdbc.sql("SELECT id, check_in_at, check_out_at FROM stay WHERE id = ? FOR UPDATE").param(stayId)
                .query((row, number) -> new Stay(row.getLong("id"), row.getObject("check_in_at", LocalDateTime.class),
                        row.getObject("check_out_at", LocalDateTime.class)))
                .optional();
    }

    private void ensureActive(long id, String message) {
        boolean withdrawn = jdbc.sql("SELECT COUNT(*) FROM absence_record WHERE id = ? AND withdrawn_at IS NOT NULL")
                .param(id).query(Integer.class).single() > 0;
        if (withdrawn) throw conflict(message);
    }

    private boolean recordExists(long stayId, LocalDate date, Long except) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM absence_record WHERE stay_id = :stay AND absence_date = :date
                    AND (:except IS NULL OR id <> :except)
                """).param("stay", stayId).param("date", date).param("except", except, java.sql.Types.BIGINT)
                .query(Integer.class).single() > 0;
    }

    private static RuntimeException duplicate() {
        return conflict("This resident already has an absence record for that date.");
    }

    private static LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private static Instant instant(LocalDateTime utcTimestamp) {
        return ResidenceTime.instant(utcTimestamp);
    }

    /** Which records a list includes. */
    public enum Status {
        ACTIVE, WITHDRAWN, ALL;

        static Status parse(String value) {
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw invalid("status must be active, withdrawn or all.");
            }
        }
    }

    /** Optional absence filters. Records are active unless a status is given. */
    public record Filter(String pattern, Status status, Long buildingId, LocalDate from, LocalDate to) {
        public static Filter of(String q, String status, Long buildingId, LocalDate from, LocalDate to) {
            Filters.ensureRange(from, to);
            return new Filter(Filters.contains(q), status == null || status.isBlank() ? Status.ACTIVE : Status.parse(status),
                    buildingId, from, to);
        }
    }

    private record Stay(long id, LocalDateTime checkInAt, LocalDateTime checkOutAt) {}

    public record Absence(long id, long stayId, LocalDate absenceDate, String reason, String studentNumber,
                          String fullName, String roomNumber, long buildingId, String buildingName, String recordedBy,
                          Instant recordedAt,
                          String updatedBy, Instant updatedAt, String status, String withdrawnBy, Instant withdrawnAt,
                          String withdrawalReason) {}
    public record NewAbsence(@Positive long stayId, @NotNull LocalDate absenceDate,
                             @NotBlank @Size(max = 240) String reason) {}
    public record Correction(@NotNull LocalDate absenceDate, @NotBlank @Size(max = 240) String reason) {}
    public record Withdrawal(@NotBlank @Size(max = 240) String reason) {}
}
