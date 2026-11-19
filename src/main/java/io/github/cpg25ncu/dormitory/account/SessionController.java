package io.github.cpg25ncu.dormitory.account;

import io.github.cpg25ncu.dormitory.bootstrap.BootstrapData;
import io.github.cpg25ncu.dormitory.common.ResidenceTime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final Accounts accounts;
    private final BootstrapData bootstrap;
    private final ResidenceTime time;

    SessionController(Accounts accounts, BootstrapData bootstrap, ResidenceTime time) {
        this.accounts = accounts;
        this.bootstrap = bootstrap;
        this.time = time;
    }

    @GetMapping("/session")
    ResponseEntity<Session> session(@AuthenticationPrincipal AccountPrincipal account, CsrfToken csrf) {
        Profile profile = account == null ? null : new Profile(account.getUsername(), account.displayName(), account.role().name());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Session(profile, csrf.getHeaderName(), csrf.getToken(), time.zone().getId(),
                        bootstrap.demo(), bootstrap.demoAccounts()));
    }

    @PutMapping("/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal AccountPrincipal account,
                                        @Valid @RequestBody Accounts.PasswordChange input,
                                        HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        accounts.changePassword(account, input);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    record Session(Profile user, String csrfHeader, String csrfToken, String timeZone, boolean demo,
                   List<BootstrapData.DemoAccount> demoAccounts) {}
    record Profile(String username, String displayName, String role) {}
}
