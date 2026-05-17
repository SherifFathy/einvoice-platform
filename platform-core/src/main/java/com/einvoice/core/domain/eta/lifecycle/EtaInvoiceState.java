package com.einvoice.core.domain.eta.lifecycle;

/** Javadoc. */
public enum EtaInvoiceState {
    DRAFT,
    SUBMITTING,
    IN_REVIEW,
    VALID,
    REJECTED,
    SUBMISSION_AMBIGUOUS,
    CANCELLED
}
