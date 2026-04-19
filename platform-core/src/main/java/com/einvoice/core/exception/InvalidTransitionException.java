package com.einvoice.core.exception;

import com.einvoice.core.domain.enums.InvoiceStatus;

/** Thrown when an invoice state transition is not allowed. */
public class InvalidTransitionException extends RuntimeException {

    private final InvoiceStatus from;
    private final InvoiceStatus to;

    /**
     * Creates a new InvalidTransitionException.
     *
     * @param from the source status
     * @param to the target status
     */
    public InvalidTransitionException(InvoiceStatus from, InvoiceStatus to) {
        super("Invalid state transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public InvoiceStatus getFrom() {
        return from;
    }

    public InvoiceStatus getTo() {
        return to;
    }
}
