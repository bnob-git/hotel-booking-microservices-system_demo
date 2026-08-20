package com.hotel.aichat.service;

import com.google.genai.errors.ClientException;
import com.hotel.aichat.dto.AuditEventRequest;
import com.hotel.aichat.dto.AuditEventType;
import com.hotel.aichat.exception.AiRateLimitException;
import com.hotel.aichat.kafka.AuditEventProducer;
import com.hotel.aichat.tools.AuditTools;
import com.hotel.aichat.tools.BookingTools;
import com.hotel.aichat.tools.UserTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
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
    private AuditEventProducer auditEventProducer;

    private ChatService chatService;

    @BeforeEach
    void setUp() {

        chatClientBuilder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);

        bookingTools = mock(BookingTools.class);
        userTools = mock(UserTools.class);
        auditTools = mock(AuditTools.class);
        chatMemory = mock(ChatMemory.class);
        auditEventProducer = mock(AuditEventProducer.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);

        chatService = new ChatService(
                chatClientBuilder,
                bookingTools,
                userTools,
                auditTools,
                chatMemory,
                auditEventProducer
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
                chatService.ask("How many bookings are there?",
                        "milan",
                        "ADMIN");

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
                () -> chatService.ask("Hello", "milan", "ADMIN")
        );

        ArgumentCaptor<AuditEventRequest> eventCaptor =
                ArgumentCaptor.forClass(AuditEventRequest.class);

        verify(auditEventProducer, times(2))
                .send(eventCaptor.capture());

        List<AuditEventRequest> events =
                eventCaptor.getAllValues();

        assertEquals(
                AuditEventType.AI_REQUEST,
                events.get(0).getEventType()
        );

        assertEquals(
                AuditEventType.AI_RATE_LIMITED,
                events.get(1).getEventType()
        );

        assertEquals(
                "milan",
                events.get(0).getActor()
        );

        assertEquals(
                "milan",
                events.get(1).getActor()
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
                () -> chatService.ask("Hello", "milan", "ADMIN")
        );

        assertSame(exception, thrown);
    }

    @Test
    void shouldBuildChatClientDuringConstruction() {

        verify(chatClientBuilder).build();
    }

    @Test
    void shouldPublishAuditEventsForSuccessfulAiRequest() {

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

        String result = chatService.ask(
                "How many bookings are there?",
                "milan",
                "ADMIN"
        );

        assertEquals(
                "There are 5 bookings.",
                result
        );

        ArgumentCaptor<AuditEventRequest> eventCaptor =
                ArgumentCaptor.forClass(AuditEventRequest.class);

        verify(auditEventProducer, times(2))
                .send(eventCaptor.capture());

        List<AuditEventRequest> events =
                eventCaptor.getAllValues();

        assertEquals(
                AuditEventType.AI_REQUEST,
                events.get(0).getEventType()
        );

        assertEquals(
                AuditEventType.AI_RESPONSE,
                events.get(1).getEventType()
        );

        assertEquals(
                "milan",
                events.get(0).getActor()
        );

        assertEquals(
                "milan",
                events.get(1).getActor()
        );

        assertEquals(
                "ai-chat-service",
                events.get(0).getServiceName()
        );

        assertEquals(
                "ai-chat-service",
                events.get(1).getServiceName()
        );
    }
}
