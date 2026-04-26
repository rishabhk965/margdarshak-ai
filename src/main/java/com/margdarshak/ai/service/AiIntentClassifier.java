package com.margdarshak.ai.service;

import com.margdarshak.ai.cache.ResponseCacheService;
import com.margdarshak.ai.model.Intent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiIntentClassifier implements IntentClassifier {

    private static final String SYSTEM_PROMPT = """
            You are an intent classifier for an Indian astrology assistant chatbot.
            Classify the user's message into exactly ONE of these intents:
            
            MUHURTA - User wants to know the best/auspicious time for an activity.
              Examples: "Best time to buy a car", "When should I start my business?",
              "Is tomorrow good for signing a contract?", "Shubh muhurat for grih pravesh"
            
            FESTIVAL_GUIDE - User wants guidance on festivals, rituals, puja steps, or religious practices.
              Examples: "How to do Diwali puja at home?", "Steps for Satyanarayan puja",
              "What to avoid during Navratri?", "Karwa Chauth vrat vidhi"
            
            ASTROLOGY_EXPLAINER - User is facing problems and wants astrological explanation, reassurance, or remedies.
              Examples: "Why am I facing constant delays?", "Why is my career stuck?",
              "Why do I feel unlucky these days?", "Kya mere kundli mein koi dosh hai?"
            
            UNKNOWN - If the message does not fit any of the above.
            
            Respond with ONLY the intent label. Nothing else. No explanation.
            """;

    private final AiService aiService;
    private final ResponseCacheService cache;

    @Override
    public Intent classify(String userMessage) {
        log.debug("Classifying intent for message: {}", userMessage);
        return cache.getIntent(userMessage).orElseGet(() -> {
            String raw = aiService.generateFast(SYSTEM_PROMPT, userMessage);
            Intent intent = Intent.fromString(raw.strip());
            log.info("Classified '{}' -> {}", userMessage, intent);
            cache.putIntent(userMessage, intent);
            return intent;
        });
    }
}
