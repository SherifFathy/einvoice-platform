package com.einvoice.core.error;

import java.util.List;

/** Javadoc. */
public class InvalidCustomerTypeException extends RuntimeException {

    public static final String CODE = "INVALID_CUSTOMER_TYPE";

    private final String field;
    private final String value;
    private final List<String> allowed;

    /**
     * Javadoc.
     *
     * @param message error message
     * @param field name of the offending field
     * @param value value that failed validation
     * @param allowed allowed values
     */
    public InvalidCustomerTypeException(String message, String field, String value, List<String> allowed) {
        super(message);
        this.field = field;
        this.value = value;
        this.allowed = allowed;
    }

    public String getCode() {
        return CODE;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    public List<String> getAllowed() {
        return allowed;
    }
}
