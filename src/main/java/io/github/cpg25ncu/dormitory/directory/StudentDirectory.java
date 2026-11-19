package io.github.cpg25ncu.dormitory.directory;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import io.github.cpg25ncu.dormitory.housing.AllocationRules;
import io.github.cpg25ncu.dormitory.housing.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDirectory {
    private final JdbcClient jdbc;
    private final AllocationRules rules;

    StudentDirectory(JdbcClient jdbc, AllocationRules rules) {
        this.jdbc = jdbc;
        this.rules = rules;
    }

    public List<Student> list() {
        return jdbc.sql("""
                SELECT s.id, s.student_number, s.full_name, s.gender, s.program, s.phone, s.email, st.id AS stay_id,
                    r.id AS room_id, r.room_number, b.name AS building_name,
                    CASE WHEN st.id IS NOT NULL THEN 'RESIDENT'
                         WHEN EXISTS (SELECT 1 FROM stay history WHERE history.student_id = s.id) THEN 'CHECKED_OUT'
                         ELSE 'UNASSIGNED' END AS status
                FROM student s LEFT JOIN stay st ON st.active_student_id = s.id
                LEFT JOIN room r ON r.id = st.room_id LEFT JOIN building b ON b.id = r.building_id
                ORDER BY s.full_name, s.id
                """).query(Student.class).list();
    }

    public void create(StudentInput input) {
        jdbc.sql("""
                INSERT INTO student (student_number, full_name, gender, program, phone, email)
                VALUES (?, ?, ?, ?, ?, ?)
                """).params(input.studentNumber().strip(), input.fullName().strip(), input.gender().name(),
                        optional(input.program(), ""), optional(input.phone(), null), optional(input.email(), null))
                .update();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(long id, StudentInput input) {
        // Locks the student first, as housing operations do, then checks any current assignment.
        rules.ensureCanChangeGender(id, input.gender());
        int changed = jdbc.sql("""
                UPDATE student SET student_number = ?, full_name = ?, gender = ?, program = ?, phone = ?, email = ?
                WHERE id = ?
                """).params(input.studentNumber().strip(), input.fullName().strip(), input.gender().name(),
                        optional(input.program(), ""), optional(input.phone(), null), optional(input.email(), null), id)
                .update();
        if (changed == 0) throw notFound("Student");
    }

    public void delete(long id) {
        if (jdbc.sql("DELETE FROM student WHERE id = ?").param(id).update() == 0) throw notFound("Student");
    }

    private static String optional(String value, String blank) {
        return value == null || value.isBlank() ? blank : value.strip();
    }

    public record Student(long id, String studentNumber, String fullName, Gender gender, String program,
                          String phone, String email, Long stayId, Long roomId, String roomNumber,
                          String buildingName, String status) {}
    public record StudentInput(@NotBlank @Size(max = 40) String studentNumber,
                               @NotBlank @Size(max = 80) String fullName,
                               @NotNull Gender gender,
                               @Size(max = 100) String program,
                               @Size(max = 30) @Pattern(regexp = "[0-9+()\\-. ]*", message = "may contain only digits, spaces and + ( ) - .") String phone,
                               @Size(max = 120) @Email String email) {}
}
