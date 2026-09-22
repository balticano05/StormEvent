package com.workspace.storm.event.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class AtlasClientException extends RuntimeException {

    public AtlasClientException(String message) {
        super(message);
    }

    public AtlasClientException(String message, Throwable cause) {
        super(message, cause);
    }

}