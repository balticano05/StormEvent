package com.workspace.storm.event.exception;

public class TicketBusServiceException extends StormException {

    public TicketBusServiceException(String message) {
        super(ErrorCode.SOURCE_ERROR, message, "ticketbus", null);
    }

    public TicketBusServiceException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_ERROR, message, cause, "ticketbus", null);
    }

}