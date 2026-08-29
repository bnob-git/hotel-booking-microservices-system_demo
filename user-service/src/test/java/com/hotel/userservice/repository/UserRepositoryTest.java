package com.hotel.userservice.repository;

import com.hotel.userservice.entity.Role;
import com.hotel.userservice.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindUserByUsername() {
        // Arrange
        User user = new User(
                "testuser",
                "password",
                Role.USER
        );

        userRepository.save(user);

        // Act
        Optional<User> result =
                userRepository.findByUsername("testuser");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        assertEquals(Role.USER, result.get().getRole());
    }

    @Test
    void shouldReturnEmptyWhenUsernameDoesNotExist() {
        // Act
        Optional<User> result =
                userRepository.findByUsername("nonexistent");

        // Assert
        assertTrue(result.isEmpty());
    }
}
