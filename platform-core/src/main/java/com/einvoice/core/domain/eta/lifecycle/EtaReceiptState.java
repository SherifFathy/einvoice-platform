package com.einvoice.core.domain.eta.lifecycle;

/** Javadoc. */
public enum EtaReceiptState {
    DRAFT,
    SUBMITTING,
    IN_REVIEW,
    VALID,
    REJECTED,
    SUBMISSION_AMBIGUOUS,
    CANCELLED
}
