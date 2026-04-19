package com.einvoice.eta.codes;

/**
 * Thrown when an ETA item code is not found.
 */
public class EtaItemCodeNotFoundException extends RuntimeException {

    /**
     * Creates a new EtaItemCodeNotFoundException.
     *
     * @param message the detail message
     */
    public EtaItemCodeNotFoundException(String message) {
        super(message);
    }
}
