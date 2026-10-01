package com.workspace.storm.event.exception;

public class BzdClientException extends StormException {

    public BzdClientException(String message) {
        super(ErrorCode.SOURCE_ERROR, message, "bzd", null);
    }

    public BzdClientException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_ERROR, message, cause, "bzd", null);
    }

}