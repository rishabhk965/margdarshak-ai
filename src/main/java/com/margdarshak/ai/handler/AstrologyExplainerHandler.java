package com.margdarshak.ai.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.margdarshak.ai.cache.ResponseCacheService;
import com.margdarshak.ai.dto.ChatResponse;
import com.margdarshak.ai.dto.ExplainerResult;
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
public class AstrologyExplainerHandler implements IntentHandler {

    private static final String SYSTEM_PROMPT = """
            You are MargDarshak, a compassionate Indian astrology assistant. The user is facing
            difficulties and seeking meaning, reassurance, and remedies.
            
            CONTEXT (current planetary data):
            %s
            
            RULES:
            - Provide a simplified astrological explanation (no heavy jargon)
            - ALWAYS include emotional reassurance -- this is critical
            - Suggest 2-4 actionable remedies (spiritual practices, habits, mindset shifts)
            - Tone must be calm, supportive, and non-alarmist
            - NEVER make deterministic predictions ("this will definitely fail")
            - NEVER create fear or panic
            - Position everything as guidance, not absolute truth
            - Remind them that difficult phases are temporary
            
            Respond ONLY as valid JSON with this structure (no markdown, no extra text):
            {"explanation":"simplified astrological explanation","reassurance":"comforting message","remedies":["remedy1","remedy2"]}
            """;

    private final AiService aiService;
    private final AstrologyService astrologyService;
    private final ObjectMapper objectMapper;
    private final ResponseCacheService cache;

    @Override
    public Intent supportedIntent() {
        return Intent.ASTROLOGY_EXPLAINER;
    }

    @Override
    public ChatResponse handle(String userMessage) {
        LocalDate today = LocalDate.now();
        ExplainerResult result = cache.compute(
                Intent.ASTROLOGY_EXPLAINER,
                userMessage,
                today, // explainer reasoning leans on today's planetary context
                ExplainerResult.class,
                cache.ttlFor(Intent.ASTROLOGY_EXPLAINER),
                () -> {
                    String context = astrologyService.getContextForDate(today);
                    String prompt = String.format(SYSTEM_PROMPT, context);
                    return parseResult(aiService.generate(prompt, userMessage));
                });

        return ChatResponse.builder()
                .intent(Intent.ASTROLOGY_EXPLAINER)
                .result(result)
                .build();
    }

    private ExplainerResult parseResult(String raw) {
        try {
            return objectMapper.readValue(raw.strip(), ExplainerResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Explainer JSON, using raw text. Error: {}", e.getMessage());
            return ExplainerResult.builder()
                    .explanation(raw.strip())
                    .reassurance("Remember, every challenge is temporary. Better times are ahead.")
                    .remedies(java.util.List.of("Stay positive and patient."))
                    .build();
        }
    }
}
