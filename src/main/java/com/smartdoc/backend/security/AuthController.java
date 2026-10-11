
package com.smartdoc.backend.security;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthController(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record AuthResponse(
            boolean authenticated,
            String email) {
    }

    
@GetMapping("/csrf")
public Map<String, String> csrf(CsrfToken token) {
    return Map.of(
        "headerName", token.getHeaderName(),
        "token", token.getToken()
    );
}

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        String email = request.email().trim().toLowerCase();

        if (repository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists"
            );
        }

        AppUser user = new AppUser(
                email,
                passwordEncoder.encode(request.password())
        );

        repository.save(user);

        return loginUser(
                email,
                request.password(),
                servletRequest,
                servletResponse
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        return loginUser(
                request.email().trim().toLowerCase(),
                request.password(),
                servletRequest,
                servletResponse
        );
    }

    private ResponseEntity<?> loginUser(
            String email,
            String password,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                            email,
                            password
                        )
                    );

            if (request.getSession(false) != null) {
                request.changeSessionId();
            }

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY,
                    context
            );

            return ResponseEntity.ok(
                    new AuthResponse(true, authentication.getName())
            );

        } catch (org.springframework.security.core.AuthenticationException ex) {
            SecurityContextHolder.clearContext();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Invalid email or password")
            );
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName().equals("anonymousUser")) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("authenticated", false)
            );
        }

        return ResponseEntity.ok(
                new AuthResponse(true, authentication.getName())
        );
    }
}
