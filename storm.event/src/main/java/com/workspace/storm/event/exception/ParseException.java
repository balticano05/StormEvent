package com.workspace.storm.event.exception;

public class ParseException extends StormException {

    private final Object partialData;

    public ParseException(String message, Object partialData) {
        super(ErrorCode.PARSE_ERROR, message);
        this.partialData = partialData;
    }

    public ParseException(String message, Throwable cause, Object partialData) {
        super(ErrorCode.PARSE_ERROR, message, cause);
        this.partialData = partialData;
    }

    public Object getPartialData() {
        return partialData;
    }

    public boolean hasPartialData() {
        return partialData != null;
    }
}