package com.einvoice.eta.codes;

/**
 * Thrown when ETA published code search fails.
 */
public class EtaSearchException extends RuntimeException {

    /**
     * Creates a new EtaSearchException.
     *
     * @param message the detail message
     */
    public EtaSearchException(String message) {
        super(message);
    }

    /**
     * Creates a new EtaSearchException with a cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public EtaSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
