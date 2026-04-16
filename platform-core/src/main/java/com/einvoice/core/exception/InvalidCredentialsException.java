package com.einvoice.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when authentication credentials are invalid. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException {

    /**
     * Creates an InvalidCredentialsException.
     *
     * @param message the detail message
     */
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
