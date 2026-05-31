package com.einvoice.core.error;

/** Javadoc. */
public class InvalidVatRateException extends RuntimeException {

    public static final String CODE = "INVALID_VAT_RATE";

    private final String field;
    private final String value;

    /**
     * Javadoc.
     *
     * @param message error message
     * @param field name of the offending field
     * @param value value that failed validation
     */
    public InvalidVatRateException(String message, String field, String value) {
        super(message);
        this.field = field;
        this.value = value;
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
}
