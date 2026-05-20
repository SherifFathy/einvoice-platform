package com.einvoice.core.domain.shared;

/** Lifecycle states for e-invoice documents. */
public enum DocumentState {
    DRAFT,
    SUBMITTING,
    SUBMITTED,
    IN_REVIEW,
    ACCEPTED,
    REJECTED,
    CANCELLED
}
