package com.einvoice.core.error;

import java.util.List;

/** Javadoc. */
public class InvalidAddressDataException extends RuntimeException {

    public static final String CODE = "INVALID_ADDRESS_DATA";

    private final List<String> missingKeys;

    public InvalidAddressDataException(String message, List<String> missingKeys) {
        super(message);
        this.missingKeys = missingKeys;
    }

    public String getCode() {
        return CODE;
    }

    public List<String> getMissingKeys() {
        return missingKeys;
    }
}
