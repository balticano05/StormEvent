package com.workspace.storm.event.exception;

public class ToolException extends StormException {

    public ToolException(String message) {
        super(ErrorCode.TOOL_ERROR, message);
    }

    public ToolException(String message, Throwable cause) {
        super(ErrorCode.TOOL_ERROR, message, cause);
    }

    public ToolException(String message, String source) {
        super(ErrorCode.TOOL_ERROR, message, source, null);
    }

    public ToolException(String message, Throwable cause, String source) {
        super(ErrorCode.TOOL_ERROR, message, cause, source, null);
    }
}