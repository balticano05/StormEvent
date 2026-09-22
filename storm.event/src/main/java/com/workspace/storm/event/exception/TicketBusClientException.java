package com.workspace.storm.event.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class TicketBusClientException extends RuntimeException {

    public TicketBusClientException(String message) {
        super(message);
    }

    public TicketBusClientException(String message, Throwable cause) {
        super(message, cause);
    }

}