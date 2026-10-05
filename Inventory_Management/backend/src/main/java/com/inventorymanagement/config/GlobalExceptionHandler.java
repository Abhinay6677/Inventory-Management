package com.inventorymanagement.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_KEY = "error";
    private static final String STATUS_KEY = "status";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                    ERROR_KEY, ex.getReason() != null ? ex.getReason() : ex.getMessage(),
                    STATUS_KEY, ex.getStatusCode().value()
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        String friendly;
        if (msg != null && msg.contains("FK") || msg != null && msg.contains("FOREIGN KEY")) {
            friendly = "Cannot delete this record because it is referenced by other data (e.g. existing orders or products).";
        } else if (msg != null && msg.contains("UNIQUE") || msg != null && msg.contains("unique constraint")) {
            friendly = "A record with the same unique value already exists.";
        } else {
            friendly = "A database constraint was violated. Please check your data.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of(ERROR_KEY, friendly, STATUS_KEY, HttpStatus.CONFLICT.value()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of(ERROR_KEY, ex.getMessage(), STATUS_KEY, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of(ERROR_KEY, "Invalid email or password", STATUS_KEY, HttpStatus.UNAUTHORIZED.value()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(Map.of(ERROR_KEY, errors, STATUS_KEY, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(ERROR_KEY, ex.getMessage(), STATUS_KEY, HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}
