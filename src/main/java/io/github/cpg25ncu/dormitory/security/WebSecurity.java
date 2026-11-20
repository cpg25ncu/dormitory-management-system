package io.github.cpg25ncu.dormitory.security;

import io.github.cpg25ncu.dormitory.account.AccountPrincipal;
import io.github.cpg25ncu.dormitory.account.Accounts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class WebSecurity {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain security(HttpSecurity http, Accounts accounts, PasswordEncoder passwords,
                                 LoginThrottle throttle) throws Exception {
        var provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwords);
        return http
                .authenticationProvider(provider)
                .authorizeHttpRequests(requests -> requests
                        // The single-page frontend is public. Data is only available through the API.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/*.js", "/*.css", "/*.svg").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/*").permitAll()
                        .requestMatchers("/api/session", "/api/login", "/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "STAFF")
                        .anyRequest().denyAll())
                .formLogin(login -> login.loginPage("/").loginProcessingUrl("/api/login")
                        .successHandler((request, response, authentication) -> {
                            throttle.recordSuccess(request.getParameter("username"), request.getRemoteAddr());
                            response.setStatus(204);
                        })
                        .failureHandler((request, response, exception) -> {
                            var lockout = throttle.recordFailure(request.getParameter("username"), request.getRemoteAddr());
                            if (lockout.isPresent()) tooManyAttempts(response, lockout.get());
                            else response.setStatus(401);
                        }))
                .logout(logout -> logout.logoutUrl("/api/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(
                        "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
                .addFilterAfter(new AccountStatusFilter(accounts), SecurityContextHolderFilter.class)
                .addFilterBefore(new LoginThrottleFilter(throttle), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void tooManyAttempts(HttpServletResponse response, Duration lockout) throws IOException {
        long minutes = Math.max(1, (lockout.toSeconds() + 59) / 60);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, lockout.toSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Too many failed sign-in attempts. "
                + "Try again in " + minutes + (minutes == 1 ? " minute" : " minutes") + ".\"}");
    }

    /** Rejects sign-in attempts for a username and address that are locked, before checking the password. */
    private static final class LoginThrottleFilter extends OncePerRequestFilter {
        private final LoginThrottle throttle;

        LoginThrottleFilter(LoginThrottle throttle) {
            this.throttle = throttle;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return !("POST".equals(request.getMethod()) && "/api/login".equals(path));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var lockout = throttle.lockedFor(request.getParameter("username"), request.getRemoteAddr());
            if (lockout.isPresent()) tooManyAttempts(response, lockout.get());
            else chain.doFilter(request, response);
        }
    }

    private static final class AccountStatusFilter extends OncePerRequestFilter {
        private final Accounts accounts;

        AccountStatusFilter(Accounts accounts) {
            this.accounts = accounts;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal account
                    && !accounts.sessionIsCurrent(account)) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
            }
            chain.doFilter(request, response);
        }
    }
}
