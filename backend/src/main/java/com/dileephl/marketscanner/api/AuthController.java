package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.config.QuantPilotSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final QuantPilotSecurityProperties securityProperties;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          QuantPilotSecurityProperties securityProperties,
                          JdbcTemplate jdbcTemplate,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.securityProperties = securityProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username().trim(), request.password()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            return ResponseEntity.ok(AuthView.authenticated(authentication.getName()));
        } catch (BadCredentialsException | AuthenticationServiceException exception) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorView("invalid_credentials", "Invalid username or password"));
        }
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@Valid @RequestBody BootstrapRequest request) {
        if (!securityProperties.enabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorView("security_disabled", "Enable database-backed security before bootstrapping a user"));
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE app_user
                   SET username = ?, password_hash = ?, enabled = TRUE, updated_at = CURRENT_TIMESTAMP
                 WHERE id = 1
                   AND password_hash IS NULL
                   AND NOT EXISTS (SELECT 1 FROM app_user WHERE enabled = TRUE)
                """,
                request.username().trim(),
                passwordEncoder.encode(request.password()));

        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorView("bootstrap_unavailable",
                            "Bootstrap is already completed or the seed account is not available"));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthView.authenticated(request.username().trim()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthView> me(Authentication authentication) {
        if (!securityProperties.enabled()) {
            return ResponseEntity.ok(AuthView.authenticated("local"));
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(AuthView.authenticated(authentication.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response,
                                       Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password) {
    }

    public record BootstrapRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required")
            @Size(min = 12, max = 128, message = "Password must contain 12 to 128 characters")
            String password) {
    }

    public record AuthView(boolean authenticated, String username) {
        static AuthView authenticated(String username) {
            return new AuthView(true, username);
        }
    }

    public record ErrorView(String error, String message) {
    }
}
