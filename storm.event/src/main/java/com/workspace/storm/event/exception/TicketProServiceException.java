package com.workspace.storm.event.exception;

public class TicketProServiceException extends StormException {

    public TicketProServiceException(String message) {
        super(ErrorCode.SOURCE_ERROR, message, "ticketpro", null);
    }

    public TicketProServiceException(String message, Throwable cause) {
        super(ErrorCode.SOURCE_ERROR, message, cause, "ticketpro", null);
    }

}