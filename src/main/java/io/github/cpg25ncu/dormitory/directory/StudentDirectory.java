package io.github.cpg25ncu.dormitory.directory;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import io.github.cpg25ncu.dormitory.common.CsvDownload;
import io.github.cpg25ncu.dormitory.common.Filters;
import io.github.cpg25ncu.dormitory.common.Page;
import io.github.cpg25ncu.dormitory.housing.AllocationRules;
import io.github.cpg25ncu.dormitory.housing.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.sql.Types;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDirectory {
    // Derived columns such as the housing status can be filtered like stored ones.
    private static final String MATCHING_STUDENTS = """
            SELECT * FROM (
                SELECT s.id, s.student_number, s.full_name, s.gender, s.program, s.phone, s.email, st.id AS stay_id,
                    r.id AS room_id, r.room_number, b.name AS building_name,
                    CASE WHEN st.id IS NOT NULL THEN 'RESIDENT'
                         WHEN EXISTS (SELECT 1 FROM stay history WHERE history.student_id = s.id) THEN 'CHECKED_OUT'
                         ELSE 'UNASSIGNED' END AS status
                FROM student s LEFT JOIN stay st ON st.active_student_id = s.id
                LEFT JOIN room r ON r.id = st.room_id LEFT JOIN building b ON b.id = r.building_id
            ) students
            WHERE (:status IS NULL OR status = :status)
                AND (:gender IS NULL OR (:gender = 'NONE' AND gender IS NULL) OR gender = :gender)
                AND (:pattern IS NULL OR LOWER(full_name) LIKE :pattern ESCAPE '!'
                    OR LOWER(student_number) LIKE :pattern ESCAPE '!' OR LOWER(program) LIKE :pattern ESCAPE '!'
                    OR LOWER(email) LIKE :pattern ESCAPE '!' OR LOWER(phone) LIKE :pattern ESCAPE '!'
                    OR LOWER(building_name) LIKE :pattern ESCAPE '!' OR LOWER(room_number) LIKE :pattern ESCAPE '!')
            """;

    private final JdbcClient jdbc;
    private final AllocationRules rules;

    StudentDirectory(JdbcClient jdbc, AllocationRules rules) {
        this.jdbc = jdbc;
        this.rules = rules;
    }

    public Page<Student> search(Filter filter, Page.Request page) {
        long total = bind(jdbc.sql("SELECT COUNT(*) FROM (" + MATCHING_STUDENTS + ") matches"), filter)
                .query(Long.class).single();
        return page.of(fetch(filter, page), total);
    }

    /** Visits every matching student in name order, reading a consistent snapshot in batches. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(Filter filter, Consumer<Student> action) {
        CsvDownload.forEachRow(page -> fetch(filter, page), action);
    }

    private List<Student> fetch(Filter filter, Page.Request page) {
        return bind(jdbc.sql(MATCHING_STUDENTS + " ORDER BY full_name, id LIMIT :limit OFFSET :offset"), filter)
                .param("limit", page.size()).param("offset", page.offset()).query(Student.class).list();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, Filter filter) {
        return statement.param("pattern", filter.pattern(), Types.VARCHAR)
                .param("status", filter.status() == null ? null : filter.status().name(), Types.VARCHAR)
                .param("gender", filter.gender() == null ? null : filter.gender().name(), Types.VARCHAR);
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

    public enum HousingStatus { RESIDENT, UNASSIGNED, CHECKED_OUT }

    /** Recorded gender, or NONE for students without one. */
    public enum GenderFilter { FEMALE, MALE, NONE }

    /** Optional student filters. {@code pattern} comes from {@link Filters#contains}. */
    public record Filter(String pattern, HousingStatus status, GenderFilter gender) {
        public static Filter of(String q, String status, String gender) {
            return new Filter(Filters.contains(q), Filters.option(HousingStatus.class, status, "status"),
                    Filters.option(GenderFilter.class, gender, "gender"));
        }
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
