package io.github.cpg25ncu.dormitory.attendance;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.invalid;
import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import io.github.cpg25ncu.dormitory.common.ResidenceTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class Absences {
    private final JdbcClient jdbc;
    private final ResidenceTime time;

    Absences(JdbcClient jdbc, ResidenceTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public List<Absence> list(AccountPrincipal account) {
        return jdbc.sql("""
                SELECT a.id, a.absence_date, a.reason, s.student_number, s.full_name,
                    r.room_number, b.name AS building_name, u.display_name AS recorded_by
                FROM absence_record a JOIN stay st ON st.id = a.stay_id JOIN student s ON s.id = st.student_id
                JOIN room r ON r.id = st.room_id JOIN building b ON b.id = r.building_id
                JOIN app_user u ON u.id = a.recorded_by
                WHERE (:admin = TRUE OR b.manager_id = :account)
                ORDER BY a.absence_date DESC, a.id DESC
                """).param("admin", account.isAdmin()).param("account", account.id()).query(Absence.class).list();
    }

    public void record(NewAbsence input, AccountPrincipal account) {
        if (input.absenceDate().isAfter(time.today())) {
            throw invalid("Absence dates must be today or earlier.");
        }
        // The resident must have checked in before the absence day ended in the residence's time zone.
        int inserted = jdbc.sql("""
                INSERT INTO absence_record (stay_id, absence_date, reason, recorded_by)
                SELECT st.id, :date, :reason, :account
                FROM stay st JOIN room r ON r.id = st.room_id JOIN building b ON b.id = r.building_id
                WHERE st.id = :stay AND st.check_out_at IS NULL
                    AND st.check_in_at < :dayEnd
                    AND (:admin = TRUE OR b.manager_id = :account)
                """).param("date", input.absenceDate()).param("reason", input.reason().strip())
                .param("dayEnd", time.startOf(input.absenceDate().plusDays(1)))
                .param("account", account.id()).param("stay", input.stayId()).param("admin", account.isAdmin())
                .update();
        if (inserted == 0) throw notFound("Active room assignment for that date");
    }

    public record Absence(long id, LocalDate absenceDate, String reason, String studentNumber,
                          String fullName, String roomNumber, String buildingName, String recordedBy) {}
    public record NewAbsence(@Positive long stayId, @NotNull LocalDate absenceDate,
                             @NotBlank @Size(max = 240) String reason) {}
}
