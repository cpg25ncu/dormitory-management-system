package io.github.cpg25ncu.dormitory.directory;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.invalid;
import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class BuildingDirectory {
    private final JdbcClient jdbc;

    BuildingDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Building> list(AccountPrincipal account) {
        return jdbc.sql("""
                SELECT b.id, b.name, b.description, b.manager_id, u.display_name AS manager_name
                FROM building b LEFT JOIN app_user u ON u.id = b.manager_id
                WHERE (:admin = TRUE OR b.manager_id = :account)
                ORDER BY b.name, b.id
                """).param("admin", account.isAdmin()).param("account", account.id()).query(Building.class).list();
    }

    public void create(BuildingInput input) {
        verifyManager(input.managerId(), null);
        jdbc.sql("INSERT INTO building (name, description, manager_id) VALUES (:name, :description, :manager)")
                .param("name", input.name().strip()).param("description", description(input))
                .param("manager", input.managerId(), java.sql.Types.BIGINT).update();
    }

    public void update(long id, BuildingInput input) {
        verifyManager(input.managerId(), id);
        int changed = jdbc.sql("""
                UPDATE building SET name = :name, description = :description, manager_id = :manager
                WHERE id = :id
                """).param("name", input.name().strip()).param("description", description(input))
                .param("manager", input.managerId(), java.sql.Types.BIGINT).param("id", id).update();
        if (changed == 0) throw notFound("Building");
    }

    public void delete(long id) {
        if (jdbc.sql("DELETE FROM building WHERE id = ?").param(id).update() == 0) throw notFound("Building");
    }

    private void verifyManager(Long id, Long buildingId) {
        if (id == null) return;
        int matches = jdbc.sql("""
                SELECT COUNT(*) FROM app_user u WHERE u.id = :id AND u.role = 'STAFF'
                AND (u.enabled = TRUE OR EXISTS (
                    SELECT 1 FROM building b WHERE b.id = :building AND b.manager_id = u.id
                ))
                """).param("id", id).param("building", buildingId, java.sql.Types.BIGINT).query(Integer.class).single();
        if (matches == 0) {
            throw invalid("Select an enabled dormitory staff account.");
        }
    }

    private String description(BuildingInput input) {
        return input.description() == null ? "" : input.description().strip();
    }

    public record Building(long id, String name, String description, Long managerId, String managerName) {}
    public record BuildingInput(@NotBlank @Size(max = 80) String name,
                                @Size(max = 240) String description, @Positive Long managerId) {}
}
