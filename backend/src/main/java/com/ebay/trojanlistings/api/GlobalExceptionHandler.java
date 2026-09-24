package com.ebay.trojanlistings.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Maps failures to the status codes in contracts/rest-api.md.
 *
 * <p>Note what is deliberately absent: there is no handler for empty, blank or
 * malformed listing content. FR-017 requires those to produce a defined verdict, not
 * an error, so they never reach here. If they do, that is a bug in the detector, not
 * a case to swallow.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Image exceeded the configured size cap. */
    public static class PayloadTooLargeException extends RuntimeException {
        public PayloadTooLargeException(String message) { super(message); }
    }

    /** Image was not PNG or JPEG. */
    public static class UnsupportedMediaTypeException extends RuntimeException {
        public UnsupportedMediaTypeException(String message) { super(message); }
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<Map<String, String>> tooLarge(PayloadTooLargeException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ResponseEntity<Map<String, String>> unsupported(UnsupportedMediaTypeException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    /**
     * Deliberate status codes must survive the catch-all below.
     *
     * <p>Without this, a 404 for an unknown run or a 503 for an unconfigured agent gets
     * flattened to 500 -- misreporting a healthy service as broken, and hiding from the
     * caller that the fix is configuration rather than a bug.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> statusException(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getReason() == null ? e.getMessage() : e.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unhandled(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Internal error: " + e.getClass().getSimpleName()));
    }
}
