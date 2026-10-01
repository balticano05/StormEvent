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
    private final MessageResolver messageResolver;

    public GlobalExceptionHandler(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @ExceptionHandler(StormException.class)
    public ResponseEntity<String> handleStormException(StormException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.warn("StormException: code={}, source={}, message={}, requestId={}",
                ex.getErrorCode(), ex.getSource(), ex.getMessage(), requestId);
        String message = messageResolver.resolve(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(message);
    }

    @ExceptionHandler(ParseException.class)
    public ResponseEntity<String> handleParseException(ParseException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.warn("ParseException: source={}, partial={}, requestId={}",
                ex.getSource(), ex.hasPartialData(), requestId);
        String message = messageResolver.resolve(ErrorCode.PARSE_ERROR, ex.getMessage());
        return ResponseEntity
                .status(ErrorCode.PARSE_ERROR.getHttpStatus())
                .body(message);
    }

    @ExceptionHandler(ToolException.class)
    public ResponseEntity<String> handleToolException(ToolException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.warn("ToolException: source={}, message={}, requestId={}",
                ex.getSource(), ex.getMessage(), requestId);
        String message = messageResolver.resolve(ErrorCode.TOOL_ERROR, ex.getMessage());
        return ResponseEntity
                .status(ErrorCode.TOOL_ERROR.getHttpStatus())
                .body(message);
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<String> handleLlmException(LlmException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.warn("LlmException: source={}, message={}, requestId={}",
                ex.getSource(), ex.getMessage(), requestId);
        String message = messageResolver.resolve(ErrorCode.LLM_UNAVAILABLE, ex.getMessage());
        return ResponseEntity
                .status(ErrorCode.LLM_UNAVAILABLE.getHttpStatus())
                .body(message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.warn("DataIntegrityViolation: requestId={}, message={}", requestId, ex.getMostSpecificCause().getMessage());
        String message = messageResolver.resolve(ErrorCode.DATA_CONSTRAINT, ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(message);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.error("DataAccessException: requestId={}", requestId, ex);
        String message = messageResolver.resolve(ErrorCode.INTERNAL_ERROR);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null
                ? "Validation failed: " + fieldError.getField() + " " + fieldError.getDefaultMessage()
                : "Validation failed";
        log.warn("ValidationException: message={}, requestId={}", message, requestId);
        String resolved = messageResolver.resolve(ErrorCode.VALIDATION_ERROR, message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(resolved);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex, HttpServletRequest request) {
        String requestId = (String) request.getAttribute("requestId");
        log.error("Internal error: requestId={}", requestId, ex);
        String message = messageResolver.resolve(ErrorCode.INTERNAL_ERROR);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(message);
    }
}