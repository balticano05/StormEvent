package com.workspace.storm.event.exception;

public class LlmException extends StormException {

    public LlmException(String message) {
        super(ErrorCode.LLM_UNAVAILABLE, message);
    }

    public LlmException(String message, Throwable cause) {
        super(ErrorCode.LLM_UNAVAILABLE, message, cause);
    }

    public LlmException(String message, String source) {
        super(ErrorCode.LLM_UNAVAILABLE, message, source, null);
    }

    public LlmException(String message, Throwable cause, String source) {
        super(ErrorCode.LLM_UNAVAILABLE, message, cause, source, null);
    }
}