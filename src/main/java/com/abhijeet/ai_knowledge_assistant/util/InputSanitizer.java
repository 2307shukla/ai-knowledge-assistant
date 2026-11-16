package com.abhijeet.ai_knowledge_assistant.util;

/**
 * Strips control characters, trims whitespace and enforces a length cap.
 * Does not attempt to block prompt injection - no filter does that reliably.
 */
public final class InputSanitizer {

    private InputSanitizer() {
    }

    public static String clean(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String text = raw
                // keep newlines and tabs, drop every other control character
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .strip();

        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        return text;
    }
}
