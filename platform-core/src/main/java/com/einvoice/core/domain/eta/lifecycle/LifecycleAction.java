package com.einvoice.core.domain.eta.lifecycle;

/** Javadoc. */
public enum LifecycleAction {
    EDIT,
    DELETE,
    SUBMIT,
    CANCEL,
    RETRY,
    CHECK_STATUS,
    CLONE_TO_NEW_DRAFT,
    MARK_VALID,
    MARK_REJECTED,
    MARK_IN_REVIEW,
    MARK_AMBIGUOUS
}
