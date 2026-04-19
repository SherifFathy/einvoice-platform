package com.einvoice.eta.codes;

/**
 * Thrown when an ETA item code belongs to a different tenant.
 */
public class EtaItemCodeAccessDeniedException extends RuntimeException {

    /**
     * Creates a new EtaItemCodeAccessDeniedException.
     *
     * @param message the detail message
     */
    public EtaItemCodeAccessDeniedException(String message) {
        super(message);
    }
}
