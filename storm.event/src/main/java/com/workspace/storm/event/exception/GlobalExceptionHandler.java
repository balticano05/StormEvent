package com.workspace.storm.event.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(StormException.class)
    public ResponseEntity<String> handleStormException(StormException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.warn("StormException: code={}, source={}, message={}, requestId={}",
                ex.getErrorCode(), ex.getSource(), ex.getMessage(), requestId);
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.warn("DataIntegrityViolation: requestId={}, message={}", requestId, ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body("Data constraint violation: " + ex.getMostSpecificCause().getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.error("DataAccessException: requestId={}", requestId, ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Database error");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null
                ? "Validation failed: " + fieldError.getField() + " " + fieldError.getDefaultMessage()
                : "Validation failed";
        log.warn("ValidationException: message={}, requestId={}", message, requestId);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.error("Internal error: requestId={}", requestId, ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error");
    }
}