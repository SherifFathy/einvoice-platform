package com.einvoice.core.error;

/** Javadoc. */
public class ChainBusyException extends RuntimeException {
    public static final String CODE = "CHAIN_BUSY";

    /**
     * Javadoc.
     *
     * @param message the error message
     */
    public ChainBusyException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
