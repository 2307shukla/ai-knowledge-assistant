package com.abhijeet.ai_knowledge_assistant.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A lightweight grounding check for retrieval-augmented answers.
 *
 * It is a heuristic, not a truth oracle: it measures how much of the answer's
 * vocabulary actually appears in the retrieved context. An answer built from
 * the context scores high; a confident invention about unrelated topics scores
 * low.
 *
 * This catches the common failure mode - the model ignoring the context and
 * answering from its own weights - and is honest about what it cannot catch:
 * a fluent answer that reuses the context's words while distorting the meaning.
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
