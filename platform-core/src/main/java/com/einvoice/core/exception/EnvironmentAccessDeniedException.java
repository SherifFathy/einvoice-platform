package com.einvoice.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when a user attempts to access an environment they lack permission for. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class EnvironmentAccessDeniedException extends RuntimeException {

    /**
     * Creates an EnvironmentAccessDeniedException.
     *
     * @param message the detail message
     */
    public EnvironmentAccessDeniedException(String message) {
        super(message);
    }
}
