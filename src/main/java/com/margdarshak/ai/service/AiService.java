package com.margdarshak.ai.service;

/**
 * Abstraction over the LLM provider. Implementations can swap between Groq,
 * Gemini, Claude, or any other backend without changing handler code.
 */
public interface AiService {

    /**
     * Send a single-turn message to the LLM with a system prompt and return the
     * text response.
     */
    String generate(String systemPrompt, String userMessage);

    /**
     * Generate a response using the fast/cheap model (for intent classification
     * and other lightweight tasks).
     */
    String generateFast(String systemPrompt, String userMessage);
}
