package com.syncstream.common;

import java.time.Instant;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, Object>> handleDuplicate() {
        return error(HttpStatus.CONFLICT, "Resource already exists");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError field = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = field == null ? "Invalid request" : field.getField() + " " + field.getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnhandled(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message == null ? status.getReasonPhrase() : message));
    }
}
