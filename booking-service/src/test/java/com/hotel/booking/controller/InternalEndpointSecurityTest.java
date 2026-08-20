package com.hotel.booking.controller;

import com.hotel.booking.config.SecurityConfig;
import com.hotel.booking.security.InternalTokenAuthFilter;
import com.hotel.booking.security.InternalTokenValidator;
import com.hotel.booking.security.JwtAuthFilter;
import com.hotel.booking.security.JwtService;
import com.hotel.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
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
    private BookingService bookingService;

    @Test
    void shouldAllowInternalCallWithValidToken() throws Exception {

        when(bookingService.existsByUserId(1L)).thenReturn(true);

        mockMvc.perform(
                        get("/api/bookings/internal/users/1/has-bookings")
                                .header(InternalTokenValidator.HEADER, INTERNAL_TOKEN)
                )
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void shouldRejectInternalCallWithoutToken() throws Exception {

        mockMvc.perform(get("/api/bookings/internal/users/1/has-bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInternalCallWithWrongToken() throws Exception {

        mockMvc.perform(
                        get("/api/bookings/internal/users/1/has-bookings")
                                .header(InternalTokenValidator.HEADER, "wrong-token")
                )
                .andExpect(status().isUnauthorized());
    }
}
