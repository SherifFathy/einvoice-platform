// THIS FILE IS GENERATED -- DO NOT EDIT
// Generated from EtaInvoiceState, EtaReceiptState Java enums
// and EtaInvoiceLifecycle / EtaReceiptLifecycle transition matrices.
// Re-run: mvn -pl platform-core process-classes

export const EtaInvoiceState = {
  DRAFT: 'DRAFT',
  SUBMITTING: 'SUBMITTING',
  IN_REVIEW: 'IN_REVIEW',
  VALID: 'VALID',
  REJECTED: 'REJECTED',
  SUBMISSION_AMBIGUOUS: 'SUBMISSION_AMBIGUOUS',
  CANCELLED: 'CANCELLED',
} as const;

export type EtaInvoiceStateType = typeof EtaInvoiceState[keyof typeof EtaInvoiceState];

export const EtaReceiptState = {
  DRAFT: 'DRAFT',
  SUBMITTING: 'SUBMITTING',
  IN_REVIEW: 'IN_REVIEW',
  VALID: 'VALID',
  REJECTED: 'REJECTED',
  SUBMISSION_AMBIGUOUS: 'SUBMISSION_AMBIGUOUS',
  CANCELLED: 'CANCELLED',
} as const;

export type EtaReceiptStateType = typeof EtaReceiptState[keyof typeof EtaReceiptState];

export const LifecycleAction = {
  EDIT: 'EDIT',
  DELETE: 'DELETE',
  SUBMIT: 'SUBMIT',
  CANCEL: 'CANCEL',
  RETRY: 'RETRY',
  CHECK_STATUS: 'CHECK_STATUS',
  CLONE_TO_NEW_DRAFT: 'CLONE_TO_NEW_DRAFT',
  MARK_VALID: 'MARK_VALID',
  MARK_REJECTED: 'MARK_REJECTED',
  MARK_IN_REVIEW: 'MARK_IN_REVIEW',
  MARK_AMBIGUOUS: 'MARK_AMBIGUOUS',
} as const;

export type LifecycleActionType = typeof LifecycleAction[keyof typeof LifecycleAction];

export const invoiceTransitions: Record<string, Record<string, string | null>> = {
  DRAFT: {
    DELETE: null,
    EDIT: 'DRAFT',
    SUBMIT: 'SUBMITTING',
  },
  SUBMITTING: {
    MARK_AMBIGUOUS: 'SUBMISSION_AMBIGUOUS',
    MARK_IN_REVIEW: 'IN_REVIEW',
    MARK_REJECTED: 'REJECTED',
    MARK_VALID: 'VALID',
  },
  IN_REVIEW: {
    CHECK_STATUS: 'IN_REVIEW',
    MARK_REJECTED: 'REJECTED',
    MARK_VALID: 'VALID',
  },
  VALID: {
    CANCEL: 'CANCELLED',
  },
  REJECTED: {
  },
  SUBMISSION_AMBIGUOUS: {
    RETRY: 'SUBMITTING',
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
    MARK_AMBIGUOUS: 'SUBMISSION_AMBIGUOUS',
    MARK_IN_REVIEW: 'IN_REVIEW',
    MARK_REJECTED: 'REJECTED',
    MARK_VALID: 'VALID',
  },
  IN_REVIEW: {
    CHECK_STATUS: 'IN_REVIEW',
    MARK_REJECTED: 'REJECTED',
    MARK_VALID: 'VALID',
  },
  VALID: {
    CANCEL: 'CANCELLED',
  },
  REJECTED: {
  },
  SUBMISSION_AMBIGUOUS: {
    RETRY: 'SUBMITTING',
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
