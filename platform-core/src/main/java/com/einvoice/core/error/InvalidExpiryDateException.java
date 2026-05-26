package com.einvoice.core.error;

/** Thrown when certificateExpiryDate cannot be parsed as a valid date. */
public class InvalidExpiryDateException extends RuntimeException {

    public static final String CODE = "VALIDATION_ERROR";

    private final String field;
    private final String value;

    /**
     * Constructs a new InvalidExpiryDateException.
     *
     * @param message detail message
     * @param field the field name that failed validation
     * @param value the invalid value that was provided
     */
    public InvalidExpiryDateException(String message, String field, String value) {
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
