package io.github.cpg25ncu.dormitory.housing;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.conflict;
import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Rooms {
    private final JdbcClient jdbc;

    Rooms(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Room> list(AccountPrincipal account) {
        return jdbc.sql("""
                SELECT r.id, r.room_number, r.building_id, b.name AS building_name, r.capacity,
                    (SELECT COUNT(*) FROM stay st WHERE st.room_id = r.id AND st.check_out_at IS NULL) AS occupied
                FROM room r JOIN building b ON b.id = r.building_id
                WHERE (:admin = TRUE OR b.manager_id = :account)
                ORDER BY b.name, r.room_number, r.id
                """).param("admin", account.isAdmin()).param("account", account.id()).query(Room.class).list();
    }

    public void create(NewRoom input) {
        if (jdbc.sql("SELECT COUNT(*) FROM building WHERE id = ?").param(input.buildingId())
                .query(Integer.class).single() == 0) {
            throw notFound("Building");
        }
        jdbc.sql("INSERT INTO room (building_id, room_number, capacity) VALUES (?, ?, ?)")
                .params(input.buildingId(), input.roomNumber().strip(), input.capacity()).update();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(long id, RoomUpdate input) {
        lockCapacity(id);
        if (input.capacity() < occupied(id)) throw conflict("Capacity cannot be less than the number of residents.");
        jdbc.sql("UPDATE room SET room_number = ?, capacity = ? WHERE id = ?")
                .params(input.roomNumber().strip(), input.capacity(), id).update();
    }

    public void delete(long id) {
        if (jdbc.sql("DELETE FROM room WHERE id = ?").param(id).update() == 0) throw notFound("Room");
    }

    int lockCapacity(long id) {
        return jdbc.sql("SELECT capacity FROM room WHERE id = ? FOR UPDATE")
                .param(id).query(Integer.class).optional().orElseThrow(() -> notFound("Room"));
    }

    int occupied(long id) {
        return jdbc.sql("SELECT COUNT(*) FROM stay WHERE room_id = ? AND check_out_at IS NULL")
                .param(id).query(Integer.class).single();
    }

    public record Room(long id, String roomNumber, long buildingId, String buildingName, int capacity, int occupied) {}
    public record NewRoom(@Positive long buildingId, @NotBlank @Size(max = 30) String roomNumber,
                          @Positive @Max(30) int capacity) {}
    public record RoomUpdate(@NotBlank @Size(max = 30) String roomNumber, @Positive @Max(30) int capacity) {}
}
