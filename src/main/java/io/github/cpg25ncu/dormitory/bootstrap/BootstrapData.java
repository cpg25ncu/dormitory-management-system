package io.github.cpg25ncu.dormitory.bootstrap;

import io.github.cpg25ncu.dormitory.account.Accounts;
import io.github.cpg25ncu.dormitory.common.ResidenceTime;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapData implements ApplicationRunner {
    private static final String DEMO_STAFF_USERNAME = "staff";
    private static final String DEMO_STAFF_PASSWORD = "DemoStaff-Only-2026";

    private final JdbcClient jdbc;
    private final Accounts accounts;
    private final ResidenceTime time;
    private final String username;
    private final String password;
    private final boolean demo;

    BootstrapData(JdbcClient jdbc, Accounts accounts, ResidenceTime time,
                  @Value("${dormitory.bootstrap.username}") String username,
                  @Value("${dormitory.bootstrap.password}") String password,
                  @Value("${dormitory.bootstrap.demo}") boolean demo) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.time = time;
        this.username = username;
        this.password = password;
        this.demo = demo;
    }

    public boolean demo() {
        return demo;
    }

    /** Sign-in options offered by the demo profile. Empty outside the demo. */
    public List<DemoAccount> demoAccounts() {
        if (!demo) return List.of();
        return List.of(new DemoAccount("ADMIN", username, password),
                new DemoAccount("STAFF", DEMO_STAFF_USERNAME, DEMO_STAFF_PASSWORD));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (accounts.hasAdmin()) return;
        if (password.isBlank()) {
            throw new IllegalStateException("Set DORM_ADMIN_PASSWORD to initialize the first administrator.");
        }
        accounts.createAdministrator(username, password);
        if (demo) seedDemo();
    }

    private void seedDemo() {
        accounts.createStaff(new Accounts.NewStaff(DEMO_STAFF_USERNAME, "Morgan Reed", DEMO_STAFF_PASSWORD));
        long adminId = accounts.loadUserByUsername(username).id();
        long staffId = accounts.loadUserByUsername(DEMO_STAFF_USERNAME).id();
        jdbc.sql("INSERT INTO building (name, description, gender_policy, manager_id) VALUES (?, ?, ?, ?)")
                .params("Cedar Hall", "North campus residence", "MIXED", staffId).update();
        jdbc.sql("INSERT INTO building (name, description, gender_policy) VALUES (?, ?, ?), (?, ?, ?)")
                .params("Maple Hall", "East campus residence for male students", "MALE_ONLY",
                        "Willow Hall", "South campus residence for female students", "FEMALE_ONLY").update();
        seedRooms("Cedar Hall", List.of("101", "102", "201"), 4);
        seedRooms("Maple Hall", List.of("110", "111", "210"), 3);
        seedRooms("Willow Hall", List.of("A01", "A02"), 2);

        record DemoStudent(String name, String gender, String program, String building, String room) {}
        var students = List.of(
                new DemoStudent("Avery Chen", "FEMALE", "Computer Science", "Cedar Hall", "101"),
                new DemoStudent("Clara Bennett", "FEMALE", "Architecture", "Cedar Hall", "101"),
                new DemoStudent("Dylan Rivera", "MALE", "Mechanical Engineering", "Cedar Hall", "102"),
                new DemoStudent("Elliot Park", "MALE", "Data Science", "Cedar Hall", "102"),
                new DemoStudent("Harper Ellis", "FEMALE", "Computer Science", "Cedar Hall", "201"),
                new DemoStudent("Jamie Patel", "MALE", "Business", "Maple Hall", "110"),
                new DemoStudent("Jordan Lee", "MALE", "Design", "Maple Hall", "111"),
                new DemoStudent("Micah Brooks", "MALE", "Civil Engineering", "Maple Hall", "210"),
                new DemoStudent("Noah Gomez", "MALE", "Mathematics", "Maple Hall", "210"),
                new DemoStudent("Robin Davis", "FEMALE", "Computer Science", "Willow Hall", "A01"),
                new DemoStudent("Sasha Okafor", "FEMALE", "Architecture", "Willow Hall", "A02"),
                new DemoStudent("Taylor Nguyen", "FEMALE", "Information Systems", "", ""));
        LocalDate today = time.today();
        int index = 0;
        for (var student : students) {
            String number = "DEMO-%03d".formatted(++index);
            String email = student.name().toLowerCase(java.util.Locale.ROOT).replace(' ', '.') + "@students.example.org";
            jdbc.sql("INSERT INTO student (student_number, full_name, gender, program, email) VALUES (?, ?, ?, ?, ?)")
                    .params(number, student.name(), student.gender(), student.program(), email).update();
            if (student.room().isEmpty()) continue;
            jdbc.sql("""
                    INSERT INTO stay (student_id, room_id, active_student_id, check_in_at, check_in_by)
                    SELECT s.id, r.id, s.id, :checkIn, :admin FROM student s CROSS JOIN room r
                    JOIN building b ON b.id = r.building_id
                    WHERE s.student_number = :number AND b.name = :building AND r.room_number = :room
                    """).param("checkIn", time.startOf(today.minusDays(14))).param("admin", adminId)
                    .param("number", number).param("building", student.building()).param("room", student.room())
                    .update();
        }
        seedAbsence("DEMO-001", today, "Approved overnight leave", staffId);
        seedAbsence("DEMO-004", today.minusDays(1), "Late return reported", staffId);
        seedAbsence("DEMO-007", today.minusDays(3), "Off-campus placement", adminId);
    }

    private void seedRooms(String building, List<String> numbers, int capacity) {
        for (String number : numbers) {
            jdbc.sql("""
                    INSERT INTO room (building_id, room_number, capacity)
                    SELECT id, :number, :capacity FROM building WHERE name = :building
                    """).param("number", number).param("capacity", capacity).param("building", building).update();
        }
    }

    private void seedAbsence(String number, LocalDate date, String reason, long user) {
        jdbc.sql("""
                INSERT INTO absence_record (stay_id, absence_date, reason, recorded_by, created_at)
                SELECT st.id, :date, :reason, :user, :now FROM stay st
                JOIN student s ON s.id = st.student_id WHERE s.student_number = :number
                """).param("date", date).param("reason", reason).param("user", user).param("number", number)
                .param("now", time.now()).update();
    }

    /** A demo sign-in shown on the sign-in page. */
    public record DemoAccount(String role, String username, String password) {}
}
