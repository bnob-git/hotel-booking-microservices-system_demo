package com.hotel.aichat.controller;

import com.hotel.aichat.exception.AiRateLimitException;
import com.hotel.aichat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import(com.hotel.aichat.exception.GlobalExceptionHandler.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void shouldReturnChatResponse() throws Exception {

        // Arrange
        when(chatService.ask("Show me all bookings"))
                .thenReturn("There are 6 bookings.");

        // Act & Assert
        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "message": "Show me all bookings"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer")
                        .value("There are 6 bookings."));
    }

    @Test
    void shouldReturn429WhenAiRateLimitIsReached() throws Exception {

        // Arrange
        when(chatService.ask(anyString()))
                .thenThrow(new AiRateLimitException(
                        "AI usage limit reached. Please try again shortly.",
                        new RuntimeException()
                ));

        // Act & Assert
        mockMvc.perform(
                        post("/api/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "message": "Show me all bookings"
                                }
                                """)
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error")
                        .value("AI usage limit reached"))
                .andExpect(jsonPath("$.message")
                        .value("AI usage limit reached. Please try again shortly."));
    }
}
