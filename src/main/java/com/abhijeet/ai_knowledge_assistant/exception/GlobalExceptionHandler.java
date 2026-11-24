package com.abhijeet.ai_knowledge_assistant.exception;

import com.abhijeet.ai_knowledge_assistant.rag.UploadRejectedException;
import com.abhijeet.ai_knowledge_assistant.security.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves the API in one shape:
 * <pre>{"status": 413, "error": "user-safe message", "requestId": "..."}</pre>
 * No exception class names, stack traces, file paths or provider messages reach the client;
 * those are logged server-side under the same request id so a report can be traced.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UploadRejectedException.class)
    public ResponseEntity<Map<String, Object>> uploadRejected(UploadRejectedException ex) {
        log.info("Upload rejected: status={}", ex.getStatus().value());
        return respond(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException ex) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> badMultipart(MultipartException ex) {
        return respond(HttpStatus.BAD_REQUEST, "The upload could not be read. Send the file as multipart form data.");
    }

    /** Malformed requests stopped by Spring Security's firewall (for example a newline in a header). */
    @ExceptionHandler(RequestRejectedException.class)
    public ResponseEntity<Map<String, Object>> rejected(RequestRejectedException ex) {
        log.warn("Request rejected by the firewall");
        return respond(HttpStatus.BAD_REQUEST, "Bad request.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> missingPart(MissingServletRequestPartException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Missing file part: " + ex.getRequestPartName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missingParam(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Missing parameter: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName() + ".");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "The request body is not valid JSON.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> mediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "Not found.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
        HttpStatusCode code = ex.getStatusCode();
        String reason = ex.getReason();
        if (code.is5xxServerError()) {
            log.error("Request failed with status {} ({})", code.value(), describe(ex));
            reason = ErrorMessages.GENERIC;
        }
        return respond(code, reason == null ? defaultMessage(code) : reason);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
        log.error("Unhandled request failure ({})", describe(ex));
        return respond(ErrorMessages.statusFor(ex), ErrorMessages.forUser(ex));
    }

    /**
     * Where a failure happened, without its message: provider and driver messages can contain keys,
     * file names or document text. The exception type and first application frame are enough to debug.
     */
    static String describe(Throwable ex) {
        String where = "unknown";
        for (StackTraceElement frame : ex.getStackTrace()) {
            if (frame.getClassName().startsWith("com.abhijeet")) {
                where = frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
                        + "." + frame.getMethodName() + ":" + frame.getLineNumber();
                break;
            }
        }
        return ex.getClass().getSimpleName() + " at " + where;
    }

    static ResponseEntity<Map<String, Object>> respond(HttpStatusCode status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        return ResponseEntity.status(status).body(body);
    }

    private static String defaultMessage(HttpStatusCode code) {
        return switch (code.value()) {
            case 400 -> "Bad request.";
            case 401 -> "Please sign in.";
            case 403 -> "Not allowed.";
            case 404 -> "Not found.";
            case 429 -> "Too many requests. Please wait a minute.";
            default -> ErrorMessages.GENERIC;
        };
    }
}
