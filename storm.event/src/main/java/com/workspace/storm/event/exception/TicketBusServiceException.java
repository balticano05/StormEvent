package com.workspace.storm.event.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class TicketBusServiceException extends RuntimeException {

    public TicketBusServiceException(String message) {
        super(message);
    }

    public TicketBusServiceException(String message, Throwable cause) {
        super(message, cause);
    }

}