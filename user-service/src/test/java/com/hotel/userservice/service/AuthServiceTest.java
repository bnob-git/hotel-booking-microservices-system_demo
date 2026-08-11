package com.hotel.userservice.service;

import com.hotel.userservice.dto.AuthResponse;
import com.hotel.userservice.dto.LoginRequest;
import com.hotel.userservice.dto.RegisterRequest;
import com.hotel.userservice.entity.Role;
import com.hotel.userservice.entity.User;
import com.hotel.userservice.kafka.AuditEventProducer;
import com.hotel.userservice.repository.UserRepository;
import com.hotel.userservice.security.CustomUserPrincipal;
import com.hotel.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditEventProducer auditEventProducer;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                auditEventProducer
        );
    }

    // =========================================================
    // INIT ADMIN
    // =========================================================

    @Test
    void shouldCreateAdminUserWhenAdminDoesNotExist() {

        // Arrange
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("admin123"))
                .thenReturn("encodedPassword");

        User savedAdmin = new User(
                "admin",
                "encodedPassword",
                Role.ADMIN
        );

        when(userRepository.save(any(User.class)))
                .thenReturn(savedAdmin);

        // Act
        authService.initAdminUser();

        // Assert
        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User admin = captor.getValue();

        assertEquals("admin", admin.getUsername());
        assertEquals("encodedPassword", admin.getPassword());
        assertEquals(Role.ADMIN, admin.getRole());

        verify(passwordEncoder).encode("admin123");
    }

    @Test
    void shouldNotCreateAdminUserWhenAdminAlreadyExists() {

        // Arrange
        User existingAdmin = new User(
                "admin",
                "encodedPassword",
                Role.ADMIN
        );

        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(existingAdmin));

        // Act
        authService.initAdminUser();

        // Assert
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    // =========================================================
    // REGISTER
    // =========================================================

    @Test
    void shouldRegisterNewUser() {

        // Arrange
        RegisterRequest request =
                new RegisterRequest("john", "password123");

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("password123"))
                .thenReturn("encodedPassword");

        User savedUser = new User(
                "john",
                "encodedPassword",
                Role.USER
        );

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        Authentication authentication =
                createAdminAuthentication();

        // Act
        authService.register(request, authentication);

        // Assert
        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User user = captor.getValue();

        assertEquals("john", user.getUsername());
        assertEquals("encodedPassword", user.getPassword());
        assertEquals(Role.USER, user.getRole());

        verify(passwordEncoder).encode("password123");
        verify(auditEventProducer).send(any());
    }

    @Test
    void shouldRejectRegistrationWhenUsernameAlreadyExists() {

        // Arrange
        RegisterRequest request =
                new RegisterRequest("john", "password123");

        User existingUser = new User(
                "john",
                "existingPassword",
                Role.USER
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(existingUser));

        Authentication authentication =
                createAdminAuthentication();

        // Act + Assert
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.register(request, authentication)
                );

        assertEquals(
                409,
                exception.getStatusCode().value()
        );

        verify(userRepository, never()).save(any(User.class));
        verify(auditEventProducer, never()).send(any());
    }

    // =========================================================
    // LOGIN
    // =========================================================

    @Test
    void shouldLoginWithCorrectCredentials() {

        // Arrange
        User user = new User(
                "john",
                "encodedPassword",
                Role.USER
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        when(jwtService.generateToken(
                anyLong(),
                anyString(),
                anyString()
        )).thenReturn("jwt-token");

        // Give the user an ID if your entity allows setId()
        user.setId(1L);

        LoginRequest request =
                new LoginRequest("john", "password123");

        // Act
        AuthResponse response =
                authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("jwt-token", response.token());

        verify(passwordEncoder).matches(
                "password123",
                "encodedPassword"
        );

        verify(jwtService).generateToken(
                1L,
                "john",
                "USER"
        );
    }

    @Test
    void shouldRejectLoginWithIncorrectPassword() {

        // Arrange
        User user = new User(
                "john",
                "encodedPassword",
                Role.USER
        );

        when(userRepository.findByUsername("john"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrongPassword",
                "encodedPassword"
        )).thenReturn(false);

        LoginRequest request =
                new LoginRequest("john", "wrongPassword");

        // Act + Assert
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.login(request)
                );

        assertEquals(
                401,
                exception.getStatusCode().value()
        );

        verify(jwtService, never())
                .generateToken(anyLong(), anyString(), anyString());
    }

    // =========================================================
    // HELPER
    // =========================================================

    private Authentication createAdminAuthentication() {

        CustomUserPrincipal principal =
                new CustomUserPrincipal(
                        1L,
                        "admin"
                );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null
        );
    }
}
