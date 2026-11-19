package io.github.cpg25ncu.dormitory.directory;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class StudentDirectory {
    private final JdbcClient jdbc;

    StudentDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Student> list() {
        return jdbc.sql("""
                SELECT s.id, s.student_number, s.full_name, s.program, st.id AS stay_id,
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
        jdbc.sql("INSERT INTO student (student_number, full_name, program) VALUES (?, ?, ?)")
                .params(input.studentNumber().strip(), input.fullName().strip(), program(input)).update();
    }

    public void update(long id, StudentInput input) {
        int changed = jdbc.sql("UPDATE student SET student_number = ?, full_name = ?, program = ? WHERE id = ?")
                .params(input.studentNumber().strip(), input.fullName().strip(), program(input), id).update();
        if (changed == 0) throw notFound("Student");
    }

    public void delete(long id) {
        if (jdbc.sql("DELETE FROM student WHERE id = ?").param(id).update() == 0) throw notFound("Student");
    }

    private String program(StudentInput input) {
        return input.program() == null ? "" : input.program().strip();
    }

    public record Student(long id, String studentNumber, String fullName, String program, Long stayId,
                          Long roomId, String roomNumber, String buildingName, String status) {}
    public record StudentInput(@NotBlank @Size(max = 40) String studentNumber,
                               @NotBlank @Size(max = 80) String fullName, @Size(max = 100) String program) {}
}
