package com.abhijeet.ai_knowledge_assistant.rag;

import org.springframework.http.HttpStatus;

/** An upload or document request that cannot be done, with a message that is safe to show the user. */
public class UploadRejectedException extends RuntimeException {

    private final HttpStatus status;

    public UploadRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
