package com.einvoice.core.error;

/** Javadoc. */
public class InvalidEnvironmentForAuthorityException extends RuntimeException {

    public static final String CODE = "INVALID_ENVIRONMENT_FOR_AUTHORITY";

    public InvalidEnvironmentForAuthorityException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
