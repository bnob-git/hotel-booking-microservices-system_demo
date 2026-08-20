package com.hotel.userservice.controller;

import com.hotel.userservice.config.SecurityConfig;
import com.hotel.userservice.entity.Role;
import com.hotel.userservice.entity.User;
import com.hotel.userservice.security.InternalTokenAuthFilter;
import com.hotel.userservice.security.InternalTokenValidator;
import com.hotel.userservice.security.JwtAuthFilter;
import com.hotel.userservice.security.JwtService;
import com.hotel.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, InternalTokenAuthFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-only-jwt-secret-key-32-bytes-minimum",
        "jwt.expirationMinutes=60",
        "internal.service-token=" + InternalEndpointSecurityTest.INTERNAL_TOKEN
})
class InternalEndpointSecurityTest {

    static final String INTERNAL_TOKEN = "test-only-internal-service-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void shouldAllowInternalCallWithValidToken() throws Exception {

        User user = new User("john", "password", Role.USER);
        user.setId(1L);

        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(
                        get("/api/users/internal/1")
                                .header(InternalTokenValidator.HEADER, INTERNAL_TOKEN)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john"));
    }

    @Test
    void shouldRejectInternalCallWithoutToken() throws Exception {

        mockMvc.perform(get("/api/users/internal/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInternalCallWithWrongToken() throws Exception {

        mockMvc.perform(
                        get("/api/users/internal/1")
                                .header(InternalTokenValidator.HEADER, "wrong-token")
                )
                .andExpect(status().isUnauthorized());
    }
}
