package com.workspace.storm.event.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class BzdClientException extends RuntimeException {

    public BzdClientException(String message) {
        super(message);
    }

    public BzdClientException(String message, Throwable cause) {
        super(message, cause);
    }

}