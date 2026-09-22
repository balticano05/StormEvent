package com.workspace.storm.event.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class AtlasServiceException extends RuntimeException {

    public AtlasServiceException(String message) {
        super(message);
    }

    public AtlasServiceException(String message, Throwable cause) {
        super(message, cause);
    }

}