package com.einvoice.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when the target company is deactivated. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class CompanyDeactivatedException extends RuntimeException {

    /**
     * Creates a CompanyDeactivatedException.
     *
     * @param message the detail message
     */
    public CompanyDeactivatedException(String message) {
        super(message);
    }
}
