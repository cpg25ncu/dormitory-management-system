package io.github.cpg25ncu.dormitory.account;

import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AccountPrincipal implements UserDetails, CredentialsContainer {
    private final long id;
    private final String username;
    private final String displayName;
    private final Role role;
    private final int version;
    private final boolean enabled;
    private String passwordHash;

    AccountPrincipal(long id, String username, String displayName, Role role, int version,
                     boolean enabled, String passwordHash) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.version = version;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public long id() { return id; }
    public String displayName() { return displayName; }
    public Role role() { return role; }
    public int version() { return version; }
    public boolean isAdmin() { return role == Role.ADMIN; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public List<SimpleGrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public void eraseCredentials() { passwordHash = null; }

    public enum Role { ADMIN, STAFF }
}
