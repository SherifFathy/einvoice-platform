package com.einvoice.core.error;

/** Javadoc. */
public class IncompatibleOriginalDocumentException extends RuntimeException {
    public static final String CODE = "INCOMPATIBLE_ORIGINAL_DOCUMENT";
    private final String expectedType;
    private final String actualType;

    /**
     * Javadoc.
     *
     * @param message the error message
     * @param expectedType the expected document type
     * @param actualType the actual document type
     */
    public IncompatibleOriginalDocumentException(String message, String expectedType, String actualType) {
        super(message);
        this.expectedType = expectedType;
        this.actualType = actualType;
    }

    public String getCode() {
        return CODE;
    }

    public String getExpectedType() {
        return expectedType;
    }

    public String getActualType() {
        return actualType;
    }
}
