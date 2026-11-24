package com.abhijeet.ai_knowledge_assistant.exception;

import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * Turns internal failures into short messages that are safe to show. Exception classes, stack
 * traces, SQL and provider responses stay in the server log only.
 */
public final class ErrorMessages {

    public static final String GENERIC = "Something went wrong on our side. Please try again.";

    private ErrorMessages() {
    }

    /** Status for an unexpected failure, recognising AI provider errors by their message. */
    public static HttpStatus statusFor(Throwable e) {
        String m = text(e);
        if (m.contains("401") || m.contains("unauthorized") || m.contains("invalid api key")
                || m.contains("api key not valid")) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (m.contains("429") || m.contains("rate limit") || m.contains("quota") || m.contains("resource_exhausted")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (m.contains("timed out") || m.contains("timeout") || m.contains("connection refused")) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public static String forUser(Throwable e) {
        return switch (statusFor(e)) {
            case BAD_GATEWAY -> "No working AI key. Add your free Gemini key in Settings, or ask the admin to set AI_API_KEY.";
            case SERVICE_UNAVAILABLE -> "The AI provider is busy or the free quota is used up. Please try again in a minute.";
            case GATEWAY_TIMEOUT -> "The AI provider took too long to answer. Please try again.";
            default -> GENERIC;
        };
    }

    private static String text(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null && sb.length() < 4000; t = t.getCause()) {
            sb.append(' ').append(t.getMessage());
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
