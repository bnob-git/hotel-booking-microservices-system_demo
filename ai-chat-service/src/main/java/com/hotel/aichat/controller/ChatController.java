package com.hotel.aichat.controller;

import com.hotel.aichat.dto.ChatRequest;
import com.hotel.aichat.dto.ChatResponse;
import com.hotel.aichat.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(
            @RequestHeader("X-Authenticated-User") String username,
            @RequestHeader("X-Authenticated-Role") String role,
            @RequestBody ChatRequest request) {

        String answer =
                chatService.ask(request.message(), username, role);

        return new ChatResponse(answer);
    }
}
