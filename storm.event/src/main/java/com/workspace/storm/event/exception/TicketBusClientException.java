package com.workspace.storm.event.exception;

public class TicketBusClientException extends StormException {

    public TicketBusClientException(String message) {
        super(ErrorCode.SOURCE_ERROR, message, "ticketbus", null);
    }

    public TicketBusClientException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_ERROR, message, cause, "ticketbus", null);
    }

}