package com.einvoice.core.error;

/** Javadoc. */
public class UnauthorizedContextException extends RuntimeException {

    public static final String CODE = "UNAUTHORIZED_CONTEXT";

    public UnauthorizedContextException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
