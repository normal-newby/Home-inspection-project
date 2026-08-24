package ca.inspection.home.inspection.advice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Catches uncaught exceptions from any @RestController and turns them into
 * consistent JSON error bodies with the right HTTP status. Without this the
 * default is a 500 with either a whitelabel HTML page or a bare stack trace —
 * neither of which the frontend can act on.
 */
@RestControllerAdvice(basePackages = "ca.inspection.home.inspection.controller")
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException e, HttpServletRequest req) {
        log.warn("404 {} {} — {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not found", e.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badArgument(IllegalArgumentException e, HttpServletRequest req) {
        log.warn("400 {} {} — {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Bad request", e.getMessage(), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e, HttpServletRequest req) {
        log.warn("413 {} {} — upload exceeded max size", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large",
                "The uploaded file exceeds the configured maximum size.", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception e, HttpServletRequest req) {
        // Log the full stack trace so we can debug; don't leak it to the client.
        log.error("500 {} {} — unhandled exception", req.getMethod(), req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. See server logs for details.", req);
    }

    private static ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String error, String message, HttpServletRequest req
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
