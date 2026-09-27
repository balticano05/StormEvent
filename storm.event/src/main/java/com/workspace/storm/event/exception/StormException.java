package com.workspace.storm.event.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public abstract class StormException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String source;
    private final Long retryAfterMs;

    protected StormException(ErrorCode errorCode, String message, String source, Long retryAfterMs) {
        super(message);
        this.errorCode = errorCode;
        this.source = source;
        this.retryAfterMs = retryAfterMs;
    }

    protected StormException(ErrorCode errorCode, String message, Throwable cause, String source, Long retryAfterMs) {
        super(message, cause);
        this.errorCode = errorCode;
        this.source = source;
        this.retryAfterMs = retryAfterMs;
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}