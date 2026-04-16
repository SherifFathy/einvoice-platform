package com.einvoice.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when a refresh token is invalid, revoked, or expired. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidRefreshTokenException extends RuntimeException {

    /**
     * Creates an InvalidRefreshTokenException.
     *
     * @param message the detail message
     */
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
