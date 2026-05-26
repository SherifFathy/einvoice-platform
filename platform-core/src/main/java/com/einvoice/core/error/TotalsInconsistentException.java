package com.einvoice.core.error;

/** Javadoc. */
public class TotalsInconsistentException extends RuntimeException {
    public static final String CODE = "TOTALS_INCONSISTENT";
    private final String field;
    private final String expected;
    private final String actual;

    /**
     * Javadoc.
     *
     * @param message the error message
     * @param field the field name
     * @param expected the expected value
     * @param actual the actual value
     */
    public TotalsInconsistentException(String message, String field, String expected, String actual) {
        super(message);
        this.field = field;
        this.expected = expected;
        this.actual = actual;
    }

    public String getCode() {
        return CODE;
    }

    public String getField() {
        return field;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }
}
