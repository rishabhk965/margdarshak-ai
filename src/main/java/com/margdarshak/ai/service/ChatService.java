package com.margdarshak.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.margdarshak.ai.dto.ChatResponse;
import com.margdarshak.ai.handler.IntentHandler;
import com.margdarshak.ai.model.ChatHistory;
import com.margdarshak.ai.model.Intent;
import com.margdarshak.ai.model.User;
import com.margdarshak.ai.repository.ChatHistoryRepository;
import com.margdarshak.ai.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private final IntentClassifier intentClassifier;
    private final Map<Intent, IntentHandler> handlers;
    private final UserRepository userRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ObjectMapper objectMapper;

    public ChatService(IntentClassifier intentClassifier,
                       List<IntentHandler> handlerList,
                       UserRepository userRepository,
                       ChatHistoryRepository chatHistoryRepository,
                       ObjectMapper objectMapper) {
        this.intentClassifier = intentClassifier;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(IntentHandler::supportedIntent, Function.identity()));
        this.userRepository = userRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatResponse processMessage(String externalUserId, String message) {
        log.info("Processing message from user={}: '{}'", externalUserId, message);

        User user = userRepository.findByExternalId(externalUserId)
                .orElseGet(() -> userRepository.save(new User(externalUserId, null)));

        Intent intent = intentClassifier.classify(message);

        IntentHandler handler = handlers.get(intent);
        ChatResponse response;
        if (handler != null) {
            response = handler.handle(message);
        } else {
            response = buildUnknownResponse(message);
        }

        saveChatHistory(user, message, response);

        return response;
    }

    private ChatResponse buildUnknownResponse(String message) {
        return ChatResponse.builder()
                .intent(Intent.UNKNOWN)
                .result(Map.of(
                        "message", "Namaste! I can help you with:\n"
                                + "1. Finding auspicious times (Muhurta) for activities\n"
                                + "2. Festival and puja guides\n"
                                + "3. Understanding life challenges through astrology\n\n"
                                + "Please try rephrasing your question around one of these topics."
                ))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void saveChatHistory(User user, String message, ChatResponse response) {
        try {
            Map<String, Object> responseMap = objectMapper.convertValue(response, Map.class);
            ChatHistory history = new ChatHistory(
                    user,
                    message,
                    response.getIntent() != null ? response.getIntent().name() : null,
                    responseMap
            );
            chatHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to save chat history for user={}", user.getExternalId(), e);
        }
    }
}
