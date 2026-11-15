package com.abhijeet.ai_knowledge_assistant.util;

/**
 * Normalises untrusted text before it reaches the model or the database.
 *
 * What this does:
 *   - strips control characters (null bytes, escape codes, etc.)
 *   - removes leading/trailing whitespace
 *   - enforces a hard length cap
 *
 * What this deliberately does NOT claim to do: stop prompt injection.
 * There is no reliable filter for that. The real mitigations are that the
 * system prompt is server-controlled, user text is never concatenated into
 * it, and the model has no tools to act with.
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
