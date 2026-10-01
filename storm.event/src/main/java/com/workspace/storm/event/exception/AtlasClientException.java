package com.workspace.storm.event.exception;

public class AtlasClientException extends StormException {

    public AtlasClientException(String message) {
        super(ErrorCode.SOURCE_ERROR, message, "atlas", null);
    }

    public AtlasClientException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_ERROR, message, cause, "atlas", null);
    }

}