package com.einvoice.core.domain.shared;

/** Actions that can trigger a lifecycle state transition. */
public enum LifecycleAction {
    EDIT,
    DELETE,
    SUBMIT,
    CANCEL,
    RETRY,
    CHECK_STATUS,
    CLONE_TO_NEW_DRAFT,
    MARK_REJECTED,
    MARK_IN_REVIEW,
    MARK_AMBIGUOUS,
    MARK_SUBMITTED,
    MARK_ACCEPTED
}
