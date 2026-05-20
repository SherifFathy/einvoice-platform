// THIS FILE IS GENERATED -- DO NOT EDIT
// Generated from DocumentState Java enum
// and LifecycleTransitions transition matrices.
// Re-run: mvn -pl platform-core process-classes

export const DocumentState = {
  DRAFT: 'DRAFT',
  SUBMITTING: 'SUBMITTING',
  SUBMITTED: 'SUBMITTED',
  IN_REVIEW: 'IN_REVIEW',
  ACCEPTED: 'ACCEPTED',
  REJECTED: 'REJECTED',
  CANCELLED: 'CANCELLED',
} as const;

export type DocumentStateType = typeof DocumentState[keyof typeof DocumentState];

export const LifecycleAction = {
  EDIT: 'EDIT',
  DELETE: 'DELETE',
  SUBMIT: 'SUBMIT',
  CANCEL: 'CANCEL',
  RETRY: 'RETRY',
  CHECK_STATUS: 'CHECK_STATUS',
  CLONE_TO_NEW_DRAFT: 'CLONE_TO_NEW_DRAFT',
  MARK_REJECTED: 'MARK_REJECTED',
  MARK_IN_REVIEW: 'MARK_IN_REVIEW',
  MARK_AMBIGUOUS: 'MARK_AMBIGUOUS',
  MARK_SUBMITTED: 'MARK_SUBMITTED',
  MARK_ACCEPTED: 'MARK_ACCEPTED',
} as const;

export type LifecycleActionType = typeof LifecycleAction[keyof typeof LifecycleAction];

export const invoiceTransitions: Record<string, Record<string, string | null>> = {
  DRAFT: {
    DELETE: null,
    EDIT: 'DRAFT',
    SUBMIT: 'SUBMITTING',
  },
  SUBMITTING: {
    MARK_ACCEPTED: 'ACCEPTED',
    MARK_AMBIGUOUS: 'IN_REVIEW',
    MARK_IN_REVIEW: 'IN_REVIEW',
    MARK_REJECTED: 'REJECTED',
  },
  SUBMITTED: {
    MARK_ACCEPTED: 'ACCEPTED',
    MARK_IN_REVIEW: 'IN_REVIEW',
    MARK_REJECTED: 'REJECTED',
  },
  IN_REVIEW: {
    CHECK_STATUS: 'IN_REVIEW',
    MARK_ACCEPTED: 'ACCEPTED',
    MARK_REJECTED: 'REJECTED',
    RETRY: 'SUBMITTING',
  },
  ACCEPTED: {
    CANCEL: 'CANCELLED',
  },
  REJECTED: {
  },
  CANCELLED: {
  },
};

export const receiptTransitions: Record<string, Record<string, string | null>> = {
  DRAFT: {
    DELETE: null,
    EDIT: 'DRAFT',
    SUBMIT: 'SUBMITTING',
  },
  SUBMITTING: {
    MARK_ACCEPTED: 'ACCEPTED',
    MARK_AMBIGUOUS: 'IN_REVIEW',
    MARK_IN_REVIEW: 'IN_REVIEW',
    MARK_REJECTED: 'REJECTED',
  },
  SUBMITTED: {
    MARK_ACCEPTED: 'ACCEPTED',
    MARK_IN_REVIEW: 'IN_REVIEW',
    MARK_REJECTED: 'REJECTED',
  },
  IN_REVIEW: {
    CHECK_STATUS: 'IN_REVIEW',
    MARK_ACCEPTED: 'ACCEPTED',
    MARK_REJECTED: 'REJECTED',
    RETRY: 'SUBMITTING',
  },
  ACCEPTED: {
    CANCEL: 'CANCELLED',
  },
  REJECTED: {
  },
  CANCELLED: {
  },
};

export function isAllowed(
    state: string, action: string,
    map?: Record<string, Record<string, string | null>>
): boolean {
  const t = map ?? invoiceTransitions;
  return t[state]?.[action] !== undefined;
}

export function nextState(
    state: string, action: string,
    map?: Record<string, Record<string, string | null>>
): string | null {
  const t = map ?? invoiceTransitions;
  const stateTransitions = t[state];
  if (!stateTransitions || !(action in stateTransitions)) {
    throw new Error(
        `Invalid lifecycle transition: ${state} + ${action}`);
  }
  return stateTransitions[action];
}
