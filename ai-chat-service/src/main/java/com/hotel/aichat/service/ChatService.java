package com.hotel.aichat.service;

import com.hotel.aichat.exception.AiRateLimitException;
import com.hotel.aichat.tools.AuditTools;
import com.hotel.aichat.tools.BookingTools;
import com.hotel.aichat.tools.UserTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    private final BookingTools bookingTools;
    private final UserTools userTools;
    private final AuditTools auditTools;
    private final ChatMemory chatMemory;

    public ChatService(
            ChatClient.Builder builder,
            BookingTools bookingTools,
            UserTools userTools,
            AuditTools auditTools,
            ChatMemory chatMemory
    ) {

        this.chatClient = builder.build();

        this.bookingTools = bookingTools;
        this.userTools = userTools;
        this.auditTools = auditTools;
        this.chatMemory = chatMemory;
    }

    public String ask(String message) {

        String systemPrompt = """
            You are an AI assistant for a hotel management system.
            
            The current user is an authenticated administrator.

            You can access hotel management data through tools:
            - bookings
            - users
            - audit events

            Rules:
            - Use tools whenever information from the hotel management system is required.
            - Never invent or guess system data.
            - If information is unavailable, clearly say so.
            - Use clear and concise formatting.
            - For lists of records, use bullet points or tables when appropriate.
            - If the user's request is ambiguous, ask for clarification.
            - Do not reveal internal tool names or implementation details.
            """;

        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .tools(
                            bookingTools,
                            userTools,
                            auditTools
                    )
                    .advisors(advisor -> advisor
                            .param(ChatMemory.CONVERSATION_ID, "admin-chat")
                    )
                    .user(message)
                    .call()
                    .content();

        } catch (RuntimeException e) {

            if (hasStatusCode(e, 429)) {
                throw new AiRateLimitException(
                        "AI usage limit reached. Please try again shortly.",
                        e
                );
            }

            throw e;
        }
    }

    private boolean hasStatusCode(Throwable exception, int statusCode) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof com.google.genai.errors.ClientException clientException
                    && clientException.code() == statusCode) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
