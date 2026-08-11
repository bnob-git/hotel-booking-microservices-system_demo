package com.hotel.userservice.service;

import com.hotel.userservice.client.BookingClient;
import com.hotel.userservice.dto.AuditEventRequest;
import com.hotel.userservice.dto.UpdateUserRequest;
import com.hotel.userservice.entity.Role;
import com.hotel.userservice.entity.User;
import com.hotel.userservice.kafka.AuditEventProducer;
import com.hotel.userservice.repository.UserRepository;
import com.hotel.userservice.security.CustomUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingClient bookingClient;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private AuditEventProducer auditEventProducer;

    @InjectMocks
    private UserService userService;

    private Authentication createAdminAuthentication() {

        CustomUserPrincipal principal =
                new CustomUserPrincipal(1L, "admin");

        return new UsernamePasswordAuthenticationToken(
                principal,
                null
        );
    }

    @Test
    void shouldGetUserById() {

        // Arrange
        User user = new User(
                "testuser",
                "password",
                Role.USER
        );

        user.setId(1L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        // Act
        User result = userService.getUserById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("testuser", result.getUsername());
        assertEquals(Role.USER, result.getRole());

        verify(userRepository).findById(1L);
    }

    @Test
    void shouldThrowNotFoundWhenUserDoesNotExist() {

        // Arrange
        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act + Assert
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.getUserById(999L)
                );

        assertEquals(404, exception.getStatusCode().value());
        assertEquals("User not found", exception.getReason());
    }

    @Test
    void shouldNotDeleteUserWithBookings() {

        // Arrange
        User user = new User(
                "testuser",
                "password",
                Role.USER
        );

        user.setId(10L);

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        when(bookingClient.hasBookings(10L))
                .thenReturn(true);

        Authentication authentication =
                createAdminAuthentication();

        // Act + Assert
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.deleteUser(10L, authentication)
                );

        assertEquals(400, exception.getStatusCode().value());

        assertEquals(
                "User has bookings and cannot be deleted",
                exception.getReason()
        );

        verify(bookingClient).hasBookings(10L);

        // Most importantly: user must NOT be deleted
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void shouldNotUpdateUserWithExistingUsername() {

        // Arrange
        User currentUser = new User(
                "oldname",
                "password",
                Role.USER
        );

        currentUser.setId(1L);

        User existingUser = new User(
                "newname",
                "password",
                Role.USER
        );

        existingUser.setId(2L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(currentUser));

        when(userRepository.findByUsername("newname"))
                .thenReturn(Optional.of(existingUser));

        UpdateUserRequest request =
                new UpdateUserRequest(
                        "newname",
                        null,
                        Role.USER
                );

        Authentication authentication =
                createAdminAuthentication();

        // Act + Assert
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.updateUser(
                                1L,
                                request,
                                authentication
                        )
                );

        assertEquals(409, exception.getStatusCode().value());

        assertEquals(
                "Username already exists",
                exception.getReason()
        );

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldDeleteUserWithoutBookings() {

        // Arrange
        User user = new User(
                "testuser",
                "password",
                Role.USER
        );

        user.setId(10L);

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        when(bookingClient.hasBookings(10L))
                .thenReturn(false);

        Authentication authentication =
                createAdminAuthentication();

        // Act
        userService.deleteUser(10L, authentication);

        // Assert
        verify(bookingClient).hasBookings(10L);
        verify(userRepository).delete(user);
        verify(auditEventProducer).send(any(AuditEventRequest.class));
    }
}
