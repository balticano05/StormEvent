package com.workspace.storm.event.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    SOURCE_TIMEOUT(HttpStatus.OK),
    SOURCE_ERROR(HttpStatus.OK),
    SOURCE_UNAVAILABLE(HttpStatus.OK),
    REQUEST_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    DATA_CONSTRAINT(HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS);

    @Getter
    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getI18nKey() {
        return "error." + name().toLowerCase().replace('_', '.');
    }
}