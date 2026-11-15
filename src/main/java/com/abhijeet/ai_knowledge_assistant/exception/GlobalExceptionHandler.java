package com.abhijeet.ai_knowledge_assistant.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every unhandled failure into readable JSON instead of the
 * Spring Boot Whitelabel error page.
 *
 * This is what lets you tell "out of credit" (401/402/429 from the provider)
 * apart from "wrong model name" or a NullPointerException.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(body("Missing parameter: " + ex.getParameterName(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
        log.error("Request failed", ex);

        String message = ex.getMessage();
        String type = ex.getClass().getSimpleName();

        // Spring AI wraps provider HTTP failures; the useful detail is in the message
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (message != null) {
            String m = message.toLowerCase();
            if (m.contains("401") || m.contains("unauthorized") || m.contains("invalid api key")) {
                status = HttpStatus.UNAUTHORIZED;
            } else if (m.contains("402") || m.contains("insufficient") || m.contains("quota")
                    || m.contains("429") || m.contains("rate limit")) {
                status = HttpStatus.TOO_MANY_REQUESTS;
            } else if (m.contains("404") || m.contains("model_not_found") || m.contains("not found")) {
                status = HttpStatus.BAD_GATEWAY;
            }
        }

        return ResponseEntity.status(status).body(body(type, message));
    }

    private Map<String, Object> body(String error, String detail) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("error", error);
        if (detail != null) {
            b.put("detail", detail);
        }
        return b;
    }
}
