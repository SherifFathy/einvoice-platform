package com.einvoice.core.error;

import java.util.List;

/** Thrown when a line's unitValue map is missing required keys. */
public class InvalidUnitValueException extends RuntimeException {

    public static final String CODE = "INVALID_UNIT_VALUE";
    private final String field;
    private final List<String> missingKeys;

    /**
     * Constructs an InvalidUnitValueException.
     *
     * @param message the error message
     * @param field the field name
     * @param missingKeys the list of missing required keys
     */
    public InvalidUnitValueException(String message, String field,
            List<String> missingKeys) {
        super(message);
        this.field = field;
        this.missingKeys = missingKeys;
    }

    public String getCode() {
        return CODE;
    }

    public String getField() {
        return field;
    }

    public List<String> getMissingKeys() {
        return missingKeys;
    }
}
