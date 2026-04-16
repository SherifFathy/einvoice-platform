package com.einvoice.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when a user has no role in the target company. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class NoRoleInCompanyException extends RuntimeException {

    /**
     * Creates a NoRoleInCompanyException.
     *
     * @param message the detail message
     */
    public NoRoleInCompanyException(String message) {
        super(message);
    }
}
