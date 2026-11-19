package io.github.cpg25ncu.dormitory.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
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
    private final boolean demo;

    SessionController(Accounts accounts, @Value("${dormitory.bootstrap.demo}") boolean demo) {
        this.accounts = accounts;
        this.demo = demo;
    }

    @GetMapping("/session")
    ResponseEntity<Session> session(@AuthenticationPrincipal AccountPrincipal account, CsrfToken csrf) {
        Profile profile = account == null ? null : new Profile(account.getUsername(), account.displayName(), account.role().name());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Session(profile, csrf.getHeaderName(), csrf.getToken(), demo));
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

    record Session(Profile user, String csrfHeader, String csrfToken, boolean demo) {}
    record Profile(String username, String displayName, String role) {}
}
