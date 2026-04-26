package com.margdarshak.ai.controller;

import com.margdarshak.ai.dto.UserRequest;
import com.margdarshak.ai.dto.UserResponse;
import com.margdarshak.ai.exception.ChatException;
import com.margdarshak.ai.model.ChatHistory;
import com.margdarshak.ai.model.User;
import com.margdarshak.ai.repository.ChatHistoryRepository;
import com.margdarshak.ai.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final ChatHistoryRepository chatHistoryRepository;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        if (userRepository.findByExternalId(request.getExternalId()).isPresent()) {
            throw new ChatException("User with externalId already exists", HttpStatus.CONFLICT);
        }

        User user = userRepository.save(new User(request.getExternalId(), request.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ChatException("User not found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(toResponse(user));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<Page<Map<String, Object>>> getChatHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!userRepository.existsById(id)) {
            throw new ChatException("User not found", HttpStatus.NOT_FOUND);
        }

        Page<Map<String, Object>> history = chatHistoryRepository
                .findByUserIdOrderByCreatedAtDesc(id, PageRequest.of(page, size))
                .map(this::toHistoryMap);

        return ResponseEntity.ok(history);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .externalId(user.getExternalId())
                .name(user.getName())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private Map<String, Object> toHistoryMap(ChatHistory h) {
        return Map.of(
                "id", h.getId(),
                "message", h.getMessage(),
                "intent", h.getIntent() != null ? h.getIntent() : "UNKNOWN",
                "response", h.getResponse(),
                "createdAt", h.getCreatedAt().toString()
        );
    }
}
