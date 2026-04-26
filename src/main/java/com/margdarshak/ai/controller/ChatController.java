package com.margdarshak.ai.controller;

import com.margdarshak.ai.dto.ChatRequest;
import com.margdarshak.ai.dto.ChatResponse;
import com.margdarshak.ai.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.processMessage(
                request.getUserId(),
                request.getMessage()
        );
        return ResponseEntity.ok(response);
    }
}
