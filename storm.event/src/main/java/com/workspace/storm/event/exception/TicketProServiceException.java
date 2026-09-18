package com.workspace.storm.event.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class TicketProServiceException extends RuntimeException {

    public TicketProServiceException(String message) {
        super(message);
    }

    public TicketProServiceException(String message, Throwable cause) {
        super(message, cause);
    }

}