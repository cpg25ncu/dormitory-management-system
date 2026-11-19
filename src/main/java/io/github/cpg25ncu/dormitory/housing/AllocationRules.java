package io.github.cpg25ncu.dormitory.housing;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.conflict;
import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Gender rules for room allocation.
 *
 * <p>A building's policy limits which students it houses, and each room houses a single gender. A
 * student needs a recorded gender before an assignment. Residents whose gender is not recorded count
 * as a mismatch, so legacy records cannot hide a conflict.
 *
 * <p>Every operation that checks or changes these facts locks rows in one order: the student, then
 * rooms by ascending ID, then the building. Concurrent check-ins, gender changes and policy changes
 * therefore see each other's committed results and cannot deadlock.
 */
@Component
public class AllocationRules {
    private final JdbcClient jdbc;
    private final Rooms rooms;

    AllocationRules(JdbcClient jdbc, Rooms rooms) {
        this.jdbc = jdbc;
        this.rooms = rooms;
    }

    /** Locks a student row and returns the recorded gender, or null when none is recorded. */
    Gender lockStudent(long studentId) {
        return jdbc.sql("SELECT gender FROM student WHERE id = ? FOR UPDATE").param(studentId)
                .query((row, number) -> new RecordedGender(Gender.parse(row.getString("gender"))))
                .optional().orElseThrow(() -> notFound("Student")).gender();
    }

    /** Checks an assignment while the caller holds the student and room locks. Locks the building. */
    void ensureCanAssign(long studentId, Gender gender, long roomId) {
        if (gender == null) throw conflict("Record this student's gender before assigning a room.");
        GenderPolicy policy = lockBuildingOf(roomId);
        if (!policy.admits(gender)) {
            throw conflict("The selected building houses " + policy.only().label() + " students only.");
        }
        if (roommatesNotRecordedAs(gender, roomId, studentId) > 0) {
            throw conflict("The selected room has residents who are not recorded as " + gender.label() + ".");
        }
    }

    /** Locks the student, then any current room and building, and checks that a new gender fits them. */
    public void ensureCanChangeGender(long studentId, Gender gender) {
        if (lockStudent(studentId) == gender) return;
        var roomId = jdbc.sql("SELECT room_id FROM stay WHERE active_student_id = ?").param(studentId)
                .query(Long.class).optional();
        if (roomId.isEmpty()) return;
        rooms.lockCapacity(roomId.get());
        if (!lockBuildingOf(roomId.get()).admits(gender) || roommatesNotRecordedAs(gender, roomId.get(), studentId) > 0) {
            throw conflict("This gender does not match the student's current building or roommates. Transfer the student first.");
        }
    }

    /** Locks a building and checks that its current residents fit a policy. */
    public void ensureCanChangePolicy(long buildingId, GenderPolicy policy) {
        lockBuilding(buildingId);
        if (policy.only() == null) return;
        int mismatched = jdbc.sql("""
                SELECT COUNT(*) FROM stay st JOIN room r ON r.id = st.room_id JOIN student s ON s.id = st.student_id
                WHERE r.building_id = ? AND st.check_out_at IS NULL AND (s.gender IS NULL OR s.gender <> ?)
                """).params(buildingId, policy.only().name()).query(Integer.class).single();
        if (mismatched > 0) {
            throw conflict("This building has residents who are not recorded as " + policy.only().label() + ".");
        }
    }

    private GenderPolicy lockBuildingOf(long roomId) {
        long buildingId = jdbc.sql("SELECT building_id FROM room WHERE id = ?").param(roomId).query(Long.class).single();
        return lockBuilding(buildingId);
    }

    private GenderPolicy lockBuilding(long buildingId) {
        return jdbc.sql("SELECT gender_policy FROM building WHERE id = ? FOR UPDATE").param(buildingId)
                .query(String.class).optional().map(GenderPolicy::valueOf).orElseThrow(() -> notFound("Building"));
    }

    private int roommatesNotRecordedAs(Gender gender, long roomId, long studentId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM stay st JOIN student s ON s.id = st.student_id
                WHERE st.room_id = ? AND st.check_out_at IS NULL AND st.student_id <> ?
                    AND (s.gender IS NULL OR s.gender <> ?)
                """).params(roomId, studentId, gender.name()).query(Integer.class).single();
    }

    private record RecordedGender(Gender gender) {}
}
