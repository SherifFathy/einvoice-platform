package com.einvoice.core.domain.enums;

/** Invoice lifecycle statuses. */
public enum InvoiceStatus {
    DRAFT,
    CANCELLED,
    VALIDATED,
    READY_FOR_SUBMISSION,
    SUBMISSION_IN_PROGRESS,
    CLEARED,
    REPORTED,
    ACCEPTED,
    IN_REVIEW,
    REJECTED,
    FAILED_RETRYABLE,
    FAILED_NON_RETRYABLE,
    SUBMISSION_AMBIGUOUS
}
