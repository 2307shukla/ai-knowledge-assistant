package com.abhijeet.ai_knowledge_assistant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("API key")) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (msg.contains("429") || msg.contains("quota") || msg.contains("rate limit")) {
            status = HttpStatus.TOO_MANY_REQUESTS;
        } else if (msg.contains("502") || msg.contains("503")) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}
