package com.einvoice.core.error;

/** Javadoc. */
public class CustomerNotFoundException extends RuntimeException {

    public static final String CODE = "CUSTOMER_NOT_FOUND";

    public CustomerNotFoundException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
