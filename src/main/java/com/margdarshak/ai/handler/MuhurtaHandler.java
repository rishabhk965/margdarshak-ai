package com.margdarshak.ai.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.margdarshak.ai.cache.ResponseCacheService;
import com.margdarshak.ai.dto.ChatResponse;
import com.margdarshak.ai.dto.MuhurtaResult;
import com.margdarshak.ai.model.Intent;
import com.margdarshak.ai.service.AiService;
import com.margdarshak.ai.service.AstrologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MuhurtaHandler implements IntentHandler {

    private static final String SYSTEM_PROMPT = """
            You are MargDarshak, a friendly Indian astrology assistant. The user wants to know
            the best/auspicious time for an activity.
            
            CONTEXT (today's astrology data):
            %s
            
            RULES:
            - Give a clear verdict: GOOD, MODERATE, or AVOID
            - If GOOD, suggest a specific time window (morning/afternoon/evening)
            - Keep the explanation simple, non-technical, 2-3 sentences max
            - Be encouraging and constructive, never fearful
            - If you must say "avoid", always suggest a better alternative time
            - Do NOT use complex astrological jargon
            
            Respond ONLY as valid JSON with this structure (no markdown, no extra text):
            {"status":"GOOD|MODERATE|AVOID","timeWindow":"e.g. 10:30 AM - 12:00 PM","message":"short advice","reason":"brief astrological reasoning"}
            """;

    private final AiService aiService;
    private final AstrologyService astrologyService;
    private final ObjectMapper objectMapper;
    private final ResponseCacheService cache;

    @Override
    public Intent supportedIntent() {
        return Intent.MUHURTA;
    }

    @Override
    public ChatResponse handle(String userMessage) {
        LocalDate today = LocalDate.now();
        MuhurtaResult result = cache.compute(
                Intent.MUHURTA,
                userMessage,
                today, // muhurta is date-sensitive: today's planetary context drives the verdict
                MuhurtaResult.class,
                cache.ttlFor(Intent.MUHURTA),
                () -> {
                    String context = astrologyService.getContextForDate(today);
                    String prompt = String.format(SYSTEM_PROMPT, context);
                    return parseResult(aiService.generate(prompt, userMessage));
                });

        return ChatResponse.builder()
                .intent(Intent.MUHURTA)
                .result(result)
                .build();
    }

    private MuhurtaResult parseResult(String raw) {
        try {
            return objectMapper.readValue(raw.strip(), MuhurtaResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Muhurta JSON, using raw text. Error: {}", e.getMessage());
            return MuhurtaResult.builder()
                    .status("MODERATE")
                    .message(raw.strip())
                    .reason("Could not determine structured astrology data.")
                    .build();
        }
    }
}
