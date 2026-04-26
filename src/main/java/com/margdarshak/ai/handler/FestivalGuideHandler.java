package com.margdarshak.ai.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.margdarshak.ai.cache.ResponseCacheService;
import com.margdarshak.ai.dto.ChatResponse;
import com.margdarshak.ai.dto.FestivalResult;
import com.margdarshak.ai.model.Intent;
import com.margdarshak.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FestivalGuideHandler implements IntentHandler {

    private static final String SYSTEM_PROMPT = """
            You are MargDarshak, a friendly Indian astrology and Hindu festival guide.
            The user wants step-by-step guidance on a festival, puja, or ritual.
            
            RULES:
            - Provide clear, numbered steps
            - For each step, explain WHAT to do and briefly WHY (spiritual/historical significance)
            - List all required items (puja samagri)
            - Mention common mistakes or things to avoid
            - Use simple, respectful language
            - Be culturally sensitive and accurate
            - Avoid overly scholarly or Sanskrit-heavy explanations
            
            Respond ONLY as valid JSON with this structure (no markdown, no extra text):
            {
              "festivalName":"name of the festival or puja",
              "steps":[
                {"stepNumber":1,"action":"what to do","reason":"why this is done"}
              ],
              "requiredItems":["item1","item2"],
              "warnings":["common mistake or thing to avoid"]
            }
            """;

    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final ResponseCacheService cache;

    @Override
    public Intent supportedIntent() {
        return Intent.FESTIVAL_GUIDE;
    }

    @Override
    public ChatResponse handle(String userMessage) {
        FestivalResult result = cache.compute(
                Intent.FESTIVAL_GUIDE,
                userMessage,
                null, // festival rituals are evergreen — no date bucket needed
                FestivalResult.class,
                cache.ttlFor(Intent.FESTIVAL_GUIDE),
                () -> parseResult(aiService.generate(SYSTEM_PROMPT, userMessage)));

        return ChatResponse.builder()
                .intent(Intent.FESTIVAL_GUIDE)
                .result(result)
                .build();
    }

    private FestivalResult parseResult(String raw) {
        try {
            return objectMapper.readValue(raw.strip(), FestivalResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Festival JSON, using raw text. Error: {}", e.getMessage());
            return FestivalResult.builder()
                    .festivalName("Unknown")
                    .warnings(java.util.List.of(raw.strip()))
                    .build();
        }
    }
}
