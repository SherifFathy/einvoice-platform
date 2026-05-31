package com.einvoice.core.error;

/** Javadoc. */
public class MissingBuyerForStandardException extends RuntimeException {
    public static final String CODE = "MISSING_BUYER_FOR_STANDARD";

    /**
     * Javadoc.
     *
     * @param message the error message
     */
    public MissingBuyerForStandardException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
