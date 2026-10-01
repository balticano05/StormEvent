package com.workspace.storm.event.exception;

public class AtlasServiceException extends StormException {

    public AtlasServiceException(String message) {
        super(ErrorCode.SOURCE_ERROR, message, "atlas", null);
    }

    public AtlasServiceException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_ERROR, message, cause, "atlas", null);
    }

}