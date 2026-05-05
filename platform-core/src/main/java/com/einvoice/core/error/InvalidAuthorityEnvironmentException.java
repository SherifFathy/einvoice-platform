package com.einvoice.core.error;

/** Javadoc. */
public class InvalidAuthorityEnvironmentException extends RuntimeException {

    public static final String CODE = "INVALID_AUTHORITY_ENVIRONMENT";

    public InvalidAuthorityEnvironmentException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
