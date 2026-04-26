package com.margdarshak.ai.model;

public enum Intent {
    MUHURTA,
    FESTIVAL_GUIDE,
    ASTROLOGY_EXPLAINER,
    UNKNOWN;

    public static Intent fromString(String value) {
        if (value == null) return UNKNOWN;
        try {
            return Intent.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
