package com.hotel.aichat.service;

import com.google.genai.errors.ClientException;
import com.hotel.aichat.exception.AiRateLimitException;
import com.hotel.aichat.tools.AuditTools;
import com.hotel.aichat.tools.BookingTools;
import com.hotel.aichat.tools.UserTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;

    private BookingTools bookingTools;
    private UserTools userTools;
    private AuditTools auditTools;
    private ChatMemory chatMemory;

    private ChatService chatService;

    @BeforeEach
    void setUp() {

        chatClientBuilder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);

        bookingTools = mock(BookingTools.class);
        userTools = mock(UserTools.class);
        auditTools = mock(AuditTools.class);
        chatMemory = mock(ChatMemory.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);

        chatService = new ChatService(
                chatClientBuilder,
                bookingTools,
                userTools,
                auditTools,
                chatMemory
        );
    }

    @Test
    void shouldReturnAiResponse() {

        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);

        ChatClient.CallResponseSpec responseSpec =
                mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);

        when(requestSpec.system(any(String.class)))
                .thenReturn(requestSpec);

        when(requestSpec.tools(
                bookingTools,
                userTools,
                auditTools
        )).thenReturn(requestSpec);

        when(requestSpec.advisors(any(Consumer.class)))
                .thenReturn(requestSpec);

        when(requestSpec.user("How many bookings are there?"))
                .thenReturn(requestSpec);

        when(requestSpec.call()).thenReturn(responseSpec);

        when(responseSpec.content())
                .thenReturn("There are 5 bookings.");

        String result =
                chatService.ask("How many bookings are there?");

        assertEquals("There are 5 bookings.", result);

        verify(chatClient).prompt();
        verify(requestSpec).user("How many bookings are there?");
        verify(requestSpec).call();
        verify(responseSpec).content();
    }

    @Test
    void shouldThrowAiRateLimitExceptionWhenGeminiReturns429() {

        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);

        when(requestSpec.system(any(String.class)))
                .thenReturn(requestSpec);

        when(requestSpec.tools(
                bookingTools,
                userTools,
                auditTools
        )).thenReturn(requestSpec);

        when(requestSpec.advisors(any(Consumer.class)))
                .thenReturn(requestSpec);

        when(requestSpec.user(any(String.class)))
                .thenReturn(requestSpec);

        when(requestSpec.call())
                .thenThrow(new ClientException(
                        429,
                        "Quota exceeded",
                        null
                ));

        assertThrows(
                AiRateLimitException.class,
                () -> chatService.ask("Hello")
        );
    }

    @Test
    void shouldPropagateNonRateLimitException() {

        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);

        RuntimeException exception =
                new RuntimeException("AI service unavailable");

        when(chatClient.prompt()).thenReturn(requestSpec);

        when(requestSpec.system(any(String.class)))
                .thenReturn(requestSpec);

        when(requestSpec.tools(
                bookingTools,
                userTools,
                auditTools
        )).thenReturn(requestSpec);

        when(requestSpec.advisors(any(Consumer.class)))
                .thenReturn(requestSpec);

        when(requestSpec.user(any(String.class)))
                .thenReturn(requestSpec);

        when(requestSpec.call())
                .thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> chatService.ask("Hello")
        );

        assertSame(exception, thrown);
    }

    @Test
    void shouldBuildChatClientDuringConstruction() {

        verify(chatClientBuilder).build();
    }
}
