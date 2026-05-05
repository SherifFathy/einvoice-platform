package com.einvoice.core.error;

/** Javadoc. */
public class BadCredentialsException extends RuntimeException {

    public static final String CODE = "BAD_CREDENTIALS";

    public BadCredentialsException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
