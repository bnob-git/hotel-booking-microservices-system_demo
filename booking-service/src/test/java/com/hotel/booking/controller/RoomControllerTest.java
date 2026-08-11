package com.hotel.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.booking.entity.Room;
import com.hotel.booking.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = RoomController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.hotel.booking.security.JwtAuthFilter.class
        )
)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoomService roomService;

    @Test
    void shouldAllowAnyoneToGetAllRooms() throws Exception {

        // Arrange
        Room room = createRoom(1L);

        when(roomService.getAllRooms())
                .thenReturn(List.of(room));

        // Act + Assert
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Room"))
                .andExpect(jsonPath("$[0].type").value("STANDARD"))
                .andExpect(jsonPath("$[0].price").value(100))
                .andExpect(jsonPath("$[0].available").value(true));

        verify(roomService).getAllRooms();
    }

    @Test
    void shouldAllowAnyoneToGetRoomById() throws Exception {

        // Arrange
        Room room = createRoom(1L);

        when(roomService.getRoomById(1L))
                .thenReturn(room);

        // Act + Assert
        mockMvc.perform(get("/api/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Room"))
                .andExpect(jsonPath("$.type").value("STANDARD"));

        verify(roomService).getRoomById(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToCreateRoom() throws Exception {

        // Arrange
        Room room = createRoom(null);
        Room savedRoom = createRoom(1L);

        when(roomService.saveRoom(any(Room.class)))
                .thenReturn(savedRoom);

        // Act + Assert
        mockMvc.perform(post("/api/rooms")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(room)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Room"));

        verify(roomService).saveRoom(any(Room.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldForbidNormalUserFromCreatingRoom() throws Exception {

        Room room = createRoom(null);

        mockMvc.perform(post("/api/rooms")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(room)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToUpdateRoom() throws Exception {

        // Arrange
        Room room = createRoom(1L);

        when(roomService.saveRoom(any(Room.class)))
                .thenReturn(room);

        // Act + Assert
        mockMvc.perform(put("/api/rooms/1")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(room)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Room"));

        verify(roomService).saveRoom(any(Room.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToDeleteRoom() throws Exception {

        // Act + Assert
        mockMvc.perform(delete("/api/rooms/1")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(roomService).deleteRoom(1L);
    }

    private Room createRoom(Long id) {
        Room room = new Room(
                "Test Room",
                "STANDARD",
                BigDecimal.valueOf(100),
                true
        );

        room.setId(id);

        return room;
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
                            .requestMatchers(HttpMethod.GET, "/api/rooms/**").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/rooms/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PUT, "/api/rooms/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.DELETE, "/api/rooms/**").hasRole("ADMIN")
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }
}
