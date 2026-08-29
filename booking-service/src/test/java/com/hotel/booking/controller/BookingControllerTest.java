package com.hotel.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.security.JwtAuthFilter;
import com.hotel.booking.service.BookingService;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = BookingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthFilter.class
        )
)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldAllowAdminToGetAllBookings() throws Exception {

        // Arrange
        BookingResponse response = new BookingResponse(
                1L,
                10L,
                20L,
                "testuser",
                "Test Room",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13)
        );

        when(bookingService.getAllBookings(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        // Act + Assert
        mockMvc.perform(get("/api/bookings").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(10))
                .andExpect(jsonPath("$.content[0].roomId").value(20))
                .andExpect(jsonPath("$.content[0].username").value("testuser"))
                .andExpect(jsonPath("$.content[0].roomName").value("Test Room"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(bookingService).getAllBookings(any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldForbidNormalUserFromGettingAllBookings() throws Exception {

        // Act + Assert
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldGetBookingById() throws Exception {

        // Arrange
        BookingResponse response = new BookingResponse(
                1L,
                10L,
                20L,
                "testuser",
                "Test Room",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13)
        );

        when(bookingService.getBookingById(1L))
                .thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/api/bookings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.roomId").value(20))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.roomName").value("Test Room"));

        verify(bookingService).getBookingById(1L);
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldGetMyBookings() throws Exception {

        // Arrange
        BookingResponse response = new BookingResponse(
                1L,
                10L,
                20L,
                "testuser",
                "Test Room",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13)
        );

        when(bookingService.getMyBookings(any()))
                .thenReturn(List.of(response));

        // Act + Assert
        mockMvc.perform(get("/api/bookings/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("testuser"));

        verify(bookingService).getMyBookings(any());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldCreateBooking() throws Exception {

        // Arrange
        BookingRequest request = new BookingRequest();
        request.setRoomId(20L);
        request.setCheckInDate(LocalDate.of(2026, 8, 11));
        request.setCheckOutDate(LocalDate.of(2026, 8, 13));

        BookingResponse response = new BookingResponse(
                1L,
                10L,
                20L,
                "testuser",
                "Test Room",
                request.getCheckInDate(),
                request.getCheckOutDate()
        );

        when(bookingService.createBooking(
                any(BookingRequest.class),
                any()
        )).thenReturn(response);

        // Act + Assert
        mockMvc.perform(post("/api/bookings")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomId").value(20))
                .andExpect(jsonPath("$.username").value("testuser"));

        verify(bookingService).createBooking(
                any(BookingRequest.class),
                any()
        );
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldDeleteBooking() throws Exception {

        // Act + Assert
        mockMvc.perform(delete("/api/bookings/1"))
                .andExpect(status().isOk());

        verify(bookingService).deleteBooking(
                eq(1L),
                any()
        );
    }

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
static class TestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .build();
    }
}

}
