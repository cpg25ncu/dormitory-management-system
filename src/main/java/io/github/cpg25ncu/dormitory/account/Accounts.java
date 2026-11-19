package io.github.cpg25ncu.dormitory.account;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.invalid;
import static io.github.cpg25ncu.dormitory.common.ApiErrors.notFound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class Accounts implements UserDetailsService {
    private static final String USERNAME_PATTERN = "[a-zA-Z0-9._-]{3,40}";
    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;

    Accounts(JdbcClient jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @Override
    public AccountPrincipal loadUserByUsername(String username) {
        return jdbc.sql("""
                SELECT id, username, display_name, role, credential_version, enabled, password_hash
                FROM app_user WHERE username = :username
                """)
                .param("username", username.strip().toLowerCase(Locale.ROOT))
                .query((row, number) -> new AccountPrincipal(
                        row.getLong("id"), row.getString("username"), row.getString("display_name"),
                        AccountPrincipal.Role.valueOf(row.getString("role")), row.getInt("credential_version"),
                        row.getBoolean("enabled"), row.getString("password_hash")))
                .optional().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
    }

    public boolean sessionIsCurrent(AccountPrincipal account) {
        return jdbc.sql("SELECT COUNT(*) FROM app_user WHERE id = ? AND enabled = TRUE AND credential_version = ?")
                .params(account.id(), account.version()).query(Integer.class).single() == 1;
    }

    public List<Staff> staff() {
        return jdbc.sql("""
                SELECT id, username, display_name, enabled FROM app_user
                WHERE role = 'STAFF' ORDER BY display_name, id
                """).query(Staff.class).list();
    }

    public void createStaff(NewStaff input) {
        create(input.username(), input.displayName(), input.password(), AccountPrincipal.Role.STAFF);
    }

    public void updateStaff(long id, StaffUpdate input) {
        int count = jdbc.sql("""
                UPDATE app_user SET display_name = ?,
                    credential_version = credential_version + CASE WHEN enabled <> ? THEN 1 ELSE 0 END, enabled = ?
                WHERE id = ? AND role = 'STAFF'
                """).params(input.displayName().strip(), input.enabled(), input.enabled(), id).update();
        if (count == 0) throw notFound("Staff account");
    }

    public void resetStaffPassword(long id, String password) {
        int count = jdbc.sql("""
                UPDATE app_user SET password_hash = ?, credential_version = credential_version + 1
                WHERE id = ? AND role = 'STAFF'
                """).params(hash(password), id).update();
        if (count == 0) throw notFound("Staff account");
    }

    public void changePassword(AccountPrincipal account, PasswordChange input) {
        String currentHash = jdbc.sql("SELECT password_hash FROM app_user WHERE id = ?")
                .param(account.id()).query(String.class).single();
        if (utf8Length(input.currentPassword()) > 72 || !passwords.matches(input.currentPassword(), currentHash)) {
            throw invalid("Current password is incorrect.");
        }
        validatePassword(input.newPassword());
        if (passwords.matches(input.newPassword(), currentHash)) throw invalid("Choose a different password.");
        int count = jdbc.sql("""
                UPDATE app_user SET password_hash = ?, credential_version = credential_version + 1
                WHERE id = ? AND credential_version = ?
                """).params(hash(input.newPassword()), account.id(), account.version()).update();
        if (count == 0) throw invalid("Your session has changed. Sign in and try again.");
    }

    public boolean hasAdmin() {
        return jdbc.sql("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN'").query(Integer.class).single() > 0;
    }

    public void createAdministrator(String username, String password) {
        create(username, "System Administrator", password, AccountPrincipal.Role.ADMIN);
    }

    private void create(String username, String displayName, String password, AccountPrincipal.Role role) {
        if (!username.matches(USERNAME_PATTERN)) throw invalid("Username must contain 3–40 letters, numbers, dots, dashes or underscores.");
        jdbc.sql("INSERT INTO app_user (username, password_hash, display_name, role) VALUES (?, ?, ?, ?)")
                .params(username.toLowerCase(Locale.ROOT), hash(password), displayName.strip(), role.name())
                .update();
    }

    private String hash(String password) {
        validatePassword(password);
        return passwords.encode(password);
    }

    private static void validatePassword(String password) {
        if (password.length() < 12 || utf8Length(password) > 72 || password.isBlank()) {
            throw invalid("Password must contain at least 12 characters and fit within 72 UTF-8 bytes.");
        }
    }

    private static int utf8Length(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length;
    }

    public record Staff(long id, String username, String displayName, boolean enabled) {}
    public record NewStaff(@NotBlank @Pattern(regexp = USERNAME_PATTERN) String username,
                           @NotBlank @Size(max = 80) String displayName,
                           @NotBlank @Size(min = 12, max = 72) String password) {}
    public record StaffUpdate(@NotBlank @Size(max = 80) String displayName, @NotNull Boolean enabled) {}
    public record PasswordReset(@NotBlank @Size(min = 12, max = 72) String password) {}
    public record PasswordChange(@NotBlank @Size(max = 72) String currentPassword,
                                 @NotBlank @Size(min = 12, max = 72) String newPassword) {}
}
