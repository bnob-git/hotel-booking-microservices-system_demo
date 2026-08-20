package com.hotel.userservice.controller;

import com.hotel.userservice.entity.Role;
import com.hotel.userservice.entity.User;
import com.hotel.userservice.security.CustomUserPrincipal;
import com.hotel.userservice.security.JwtAuthFilter;
import com.hotel.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class
        )
)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void shouldAllowAdminToGetAllUsers() throws Exception {

        // Arrange
        User user = new User(
                "john",
                "password",
                Role.USER
        );

        user.setId(1L);

        when(userService.getAllUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

        // Act + Assert
        mockMvc.perform(
                        get("/api/users")
                                .param("page", "0")
                                .param("size", "20")
                                .with(authentication(createAdminAuthentication()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].username").value("john"))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldForbidNormalUserFromGettingAllUsers() throws Exception {

        mockMvc.perform(
                        get("/api/users")
                                .with(authentication(createUserAuthentication()))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldGetCurrentUser() throws Exception {

        // Arrange
        User user = new User(
                "john",
                "password",
                Role.USER
        );

        user.setId(1L);

        when(userService.getUserByUsername("john"))
                .thenReturn(user);

        // Act + Assert
        mockMvc.perform(
                        get("/api/users/me")
                                .with(authentication(createUserAuthentication()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    // =========================================================
    // AUTHENTICATION HELPERS
    // =========================================================

    private Authentication createAdminAuthentication() {

        CustomUserPrincipal principal =
                new CustomUserPrincipal(
                        1L,
                        "admin"
                );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(() ->
                        "ROLE_ADMIN"
                )
        );
    }

    private Authentication createUserAuthentication() {

        CustomUserPrincipal principal =
                new CustomUserPrincipal(
                        2L,
                        "john"
                );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(() ->
                        "ROLE_USER"
                )
        );
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http)
                throws Exception {

            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }
}
