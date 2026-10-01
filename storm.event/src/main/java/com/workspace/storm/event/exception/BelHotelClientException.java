package com.workspace.storm.event.exception;

public class BelHotelClientException extends StormException {

    public BelHotelClientException(String message) {
        super(ErrorCode.SOURCE_ERROR, message, "belhotel", null);
    }

    public BelHotelClientException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_ERROR, message, cause, "belhotel", null);
    }

}