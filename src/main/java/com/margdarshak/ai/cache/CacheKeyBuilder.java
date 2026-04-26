package com.margdarshak.ai.cache;

import com.margdarshak.ai.model.Intent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds deterministic cache keys for LLM responses.
 *
 * Format: {intentLabel}:{dateBucket-or-_}:{sha256-hex}
 * Length: intentLabel(<=25) + 1 + 10 + 1 + 64 = ~101 chars (fits VARCHAR(160)).
 *
 * The SHA-256 input includes the configured prompt-version so a prompt change
 * globally invalidates all cached responses without touching the DB.
 */
@Component
public class CacheKeyBuilder {

    /**
     * Sentinel "intent" used for the intent-classifier cache. Picked to be
     * outside the {@link Intent} enum's name space so it can never collide
     * with a real intent's response cache key.
     */
    public static final String CLASSIFIER_LABEL = "__INTENT__";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // Strips emojis and most punctuation; keeps unicode letters/digits and spaces.
    private static final Pattern NON_TEXT = Pattern.compile("[^\\p{L}\\p{Nd}\\s]");

    private final String promptVersion;

    public CacheKeyBuilder(@Value("${cache.prompt-version:v1}") String promptVersion) {
        this.promptVersion = promptVersion;
    }

    /** Cache key for an intent handler's generated response. */
    public String forResponse(Intent intent, String message, LocalDate dateBucket) {
        return build(intent.name(), message, dateBucket);
    }

    /** Cache key for the intent classifier (date-independent). */
    public String forClassifier(String message) {
        return build(CLASSIFIER_LABEL, message, null);
    }

    private String build(String intentLabel, String message, LocalDate dateBucket) {
        String normalized = normalize(message);
        String hashInput = promptVersion + "|" + intentLabel + "|" + normalized;
        String hash = sha256Hex(hashInput);
        String bucket = dateBucket != null ? dateBucket.toString() : "_";
        return intentLabel + ":" + bucket + ":" + hash;
    }

    /** Lowercase, strip emojis/punctuation, collapse whitespace, trim. Visible for testing. */
    static String normalize(String message) {
        if (message == null) return "";
        String stripped = NON_TEXT.matcher(message).replaceAll(" ");
        String collapsed = WHITESPACE.matcher(stripped).replaceAll(" ").trim();
        return collapsed.toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the JVM platform; this should never happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
