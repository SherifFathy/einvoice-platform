package com.einvoice.core.error;

/** Javadoc. */
public class WrongOriginalClassException extends RuntimeException {
    public static final String CODE = "WRONG_ORIGINAL_CLASS";
    private final String expectedClass;
    private final String actualClass;

    /**
     * Javadoc.
     *
     * @param message the error message
     * @param expectedClass the expected document class
     * @param actualClass the actual document class
     */
    public WrongOriginalClassException(String message, String expectedClass, String actualClass) {
        super(message);
        this.expectedClass = expectedClass;
        this.actualClass = actualClass;
    }

    public String getCode() {
        return CODE;
    }

    public String getExpectedClass() {
        return expectedClass;
    }

    public String getActualClass() {
        return actualClass;
    }
}
