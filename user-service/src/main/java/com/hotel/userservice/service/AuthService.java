package com.hotel.userservice.service;

import com.hotel.userservice.dto.*;
import com.hotel.userservice.entity.Role;
import com.hotel.userservice.entity.User;
import com.hotel.userservice.kafka.AuditEventProducer;
import com.hotel.userservice.repository.UserRepository;
import com.hotel.userservice.security.CustomUserPrincipal;
import com.hotel.userservice.security.JwtService;
import com.hotel.userservice.security.LoginAttemptService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditEventProducer auditEventProducer;
    private final LoginAttemptService loginAttemptService;
    private final String adminUsername;
    private final String adminPassword;
    private static final Logger log =
            LoggerFactory.getLogger(AuthService.class);

    // BCrypt hash of a value that cannot be produced by any real password,
    // used to keep the encoder cost identical for unknown usernames.
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa1u0hZ9hVQfKcXVvKtI5rqRfR8bJVLK";

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditEventProducer auditEventProducer,
                       LoginAttemptService loginAttemptService,
                       @Value("${admin.username}") String adminUsername,
                       @Value("${admin.password}") String adminPassword) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditEventProducer = auditEventProducer;
        this.loginAttemptService = loginAttemptService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    public void initAdminUser() {

        if (adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {

            log.warn("Bootstrap admin credentials are not configured, skipping admin creation");
            return;
        }

        userRepository.findByUsername(adminUsername)
                .orElseGet(() -> {

                    User admin = new User();

                    admin.setUsername(adminUsername);
                    admin.setPassword(passwordEncoder.encode(adminPassword));
                    admin.setRole(Role.ADMIN);

                    return userRepository.save(admin);
                });
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void register(RegisterRequest request, Authentication authentication) {

        userRepository.findByUsername(request.username())
                .ifPresent(user -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Username already exists"
                    );
                });

        User newUser = new User();

        newUser.setUsername(request.username());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setRole(Role.USER);

        User savedUser = userRepository.save(newUser);

        // Send audit event
        CustomUserPrincipal admin =
                (CustomUserPrincipal) authentication.getPrincipal();

        Map<String, Object> payload = Map.of(
                "username", savedUser.getUsername(),
                "role", savedUser.getRole().name()
        );

        AuditEventRequest auditEvent = new AuditEventRequest(
                UUID.randomUUID(),
                AuditEventType.USER_REGISTERED,
                "user-service",
                admin.getUsername(),
                "USER",
                savedUser.getId(),
                payload,
                "User registered successfully"
        );

        try {
            auditEventProducer.send(auditEvent);
        } catch (Exception e) {
            log.error("Failed to send audit event for user {}", savedUser.getId(), e);
        }
    }

    public AuthResponse login(LoginRequest request) {

        if (loginAttemptService.isLocked(request.username())) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts, try again later"
            );
        }

        User user = userRepository.findByUsername(request.username())
                .orElse(null);

        // The encoder runs even for unknown users so response times do not
        // reveal whether the username exists.
        String storedPassword = user != null ? user.getPassword() : DUMMY_PASSWORD_HASH;

        boolean passwordMatches =
                passwordEncoder.matches(request.password(), storedPassword) && user != null;

        if (!passwordMatches) {
            loginAttemptService.recordFailure(request.username());

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid credentials"
            );
        }

        loginAttemptService.reset(request.username());

        String token = jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );

        return new AuthResponse(token);
    }
}
