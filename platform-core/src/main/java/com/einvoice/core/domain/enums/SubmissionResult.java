package com.einvoice.core.domain.enums;

/** Result of a submission attempt to a tax authority. */
public enum SubmissionResult {
    SUCCESS,
    REJECTED,
    ERROR,
    TIMEOUT,
    AMBIGUOUS
}
