package com.einvoice.core.error;

/** Javadoc. */
public class InactiveCompanyException extends RuntimeException {

    public static final String CODE = "INACTIVE_COMPANY";

    public InactiveCompanyException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
