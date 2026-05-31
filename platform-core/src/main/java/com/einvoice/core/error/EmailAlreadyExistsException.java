package com.einvoice.core.error;

/** Javadoc. */
public class EmailAlreadyExistsException extends RuntimeException {

    public static final String CODE = "EMAIL_ALREADY_EXISTS";

    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
