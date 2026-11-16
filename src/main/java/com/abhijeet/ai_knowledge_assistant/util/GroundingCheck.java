package com.abhijeet.ai_knowledge_assistant.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Measures how much of an answer's vocabulary appears in the retrieved context.
 * A heuristic for flagging ungrounded answers, not proof of correctness.
 */
public final class GroundingCheck {

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "that", "this", "with", "from", "have", "has", "had",
            "are", "was", "were", "will", "would", "can", "could", "should", "not",
            "but", "you", "your", "they", "them", "their", "there", "here", "what",
            "when", "where", "which", "who", "how", "why", "into", "about", "also",
            "than", "then", "such", "some", "any", "all", "more", "most", "only"));

    private GroundingCheck() {
    }

    public static double overlap(String answer, String context) {
        Set<String> answerTokens = tokens(answer);
        if (answerTokens.isEmpty()) {
            return 1.0;
        }
        Set<String> contextTokens = tokens(context);
        long hits = answerTokens.stream().filter(contextTokens::contains).count();
        return (double) hits / answerTokens.size();
    }

    public static boolean isGrounded(String answer, String context, double threshold) {
        return overlap(answer, context) >= threshold;
    }

    private static Set<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 3)
                .filter(token -> !STOPWORDS.contains(token))
                .collect(Collectors.toSet());
    }
}
