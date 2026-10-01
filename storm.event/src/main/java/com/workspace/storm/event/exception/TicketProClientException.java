package com.workspace.storm.event.exception;

public class TicketProClientException extends StormException {

    public TicketProClientException(String message) {
        super(ErrorCode.SOURCE_ERROR, message, "ticketpro", null);
    }

    public TicketProClientException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_ERROR, message, cause, "ticketpro", null);
    }

}