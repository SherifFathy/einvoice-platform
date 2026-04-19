package com.einvoice.eta.codes;

/**
 * Thrown when a duplicate ETA item code is registered for the same company.
 */
public class DuplicateEtaItemCodeException extends RuntimeException {

    /**
     * Creates a new DuplicateEtaItemCodeException.
     *
     * @param message the detail message
     */
    public DuplicateEtaItemCodeException(String message) {
        super(message);
    }
}
