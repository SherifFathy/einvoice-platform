package com.einvoice.core.domain.shared;

import com.einvoice.core.domain.shared.LifecycleAction;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Central lifecycle transition matrix.
 * Defines allowed actions and target states per {@link TransactionType}.
 */
public final class LifecycleTransitions {

    private static final Map<TransactionType, Map<DocumentState, Set<LifecycleAction>>> ALLOWED;
    private static final Map<TransactionType,
            Map<DocumentState, Map<LifecycleAction, DocumentState>>> TRANSITIONS;

    static {
        ALLOWED = new EnumMap<>(TransactionType.class);
        TRANSITIONS = new EnumMap<>(TransactionType.class);

        initInvoiceMatrix();
        initReceiptMatrix();
        initStandardMatrix();
        initSimplifiedMatrix();
    }

    private LifecycleTransitions() {
    }

    /**
     * Check whether a given action is allowed from the supplied state.
     *
     * @param from source state
     * @param action the lifecycle action
     * @param txType transaction type
     * @return true if the action is allowed
     */
    public static boolean allowed(DocumentState from, LifecycleAction action,
            TransactionType txType) {
        Map<DocumentState, Set<LifecycleAction>> matrix = ALLOWED.get(txType);
        if (matrix == null) {
            return false;
        }
        Set<LifecycleAction> actions = matrix.get(from);
        return actions != null && actions.contains(action);
    }

    /**
     * Resolve the target state for a given action, or throw if not allowed.
     *
     * @param from source state
     * @param action the lifecycle action
     * @param txType transaction type
     * @return the target document state
     * @throws InvalidLifecycleTransitionException if the transition is not allowed
     */
    public static DocumentState next(DocumentState from, LifecycleAction action,
            TransactionType txType) {
        if (!allowed(from, action, txType)) {
            throw new InvalidLifecycleTransitionException(
                    "Transition not allowed: " + from + " + " + action
                            + " for " + txType,
                    from.name(), action.name());
        }
        if (action == LifecycleAction.DELETE) {
            return null;
        }
        Map<DocumentState, Map<LifecycleAction, DocumentState>> txMatrix =
                TRANSITIONS.get(txType);
        if (txMatrix == null) {
            throw new InvalidLifecycleTransitionException(
                    "No transition matrix for " + txType,
                    from.name(), action.name());
        }
        Map<LifecycleAction, DocumentState> targets = txMatrix.get(from);
        if (targets == null || !targets.containsKey(action)) {
            throw new InvalidLifecycleTransitionException(
                    "No target state defined for: " + from + " + " + action
                            + " for " + txType,
                    from.name(), action.name());
        }
        return targets.get(action);
    }

    /**
     * Assert that a direct state-to-state transition exists.
     *
     * @param from source state
     * @param to target state
     * @param txType transaction type
     * @throws InvalidLifecycleTransitionException if no matching transition exists
     */
    public static void assertAllowed(DocumentState from, DocumentState to,
            TransactionType txType) {
        if (from == to) {
            return;
        }
        Map<DocumentState, Map<LifecycleAction, DocumentState>> txMatrix =
                TRANSITIONS.get(txType);
        if (txMatrix == null) {
            throw new InvalidLifecycleTransitionException(
                    "No transition matrix for " + txType,
                    from.name(), to.name());
        }
        Map<LifecycleAction, DocumentState> targets = txMatrix.get(from);
        if (targets == null || !targets.containsValue(to)) {
            throw new InvalidLifecycleTransitionException(
                    "Transition not allowed: " + from + " -> " + to
                            + " for " + txType,
                    from.name(), to.name());
        }
    }

    private static void initInvoiceMatrix() {
        Map<DocumentState, Set<LifecycleAction>> allowedMap =
                new EnumMap<>(DocumentState.class);
        allowedMap.put(DocumentState.DRAFT, EnumSet.of(
                LifecycleAction.EDIT, LifecycleAction.DELETE,
                LifecycleAction.SUBMIT));
        allowedMap.put(DocumentState.SUBMITTING, EnumSet.of(
                LifecycleAction.MARK_ACCEPTED, LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW, LifecycleAction.MARK_AMBIGUOUS));
        allowedMap.put(DocumentState.SUBMITTED, EnumSet.of(
                LifecycleAction.MARK_ACCEPTED, LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW));
        allowedMap.put(DocumentState.IN_REVIEW, EnumSet.of(
                LifecycleAction.CHECK_STATUS, LifecycleAction.MARK_ACCEPTED,
                LifecycleAction.MARK_REJECTED, LifecycleAction.RETRY));
        allowedMap.put(DocumentState.ACCEPTED, EnumSet.of(
                LifecycleAction.CANCEL));
        allowedMap.put(DocumentState.REJECTED, EnumSet.noneOf(
                LifecycleAction.class));
        allowedMap.put(DocumentState.CANCELLED, EnumSet.noneOf(
                LifecycleAction.class));
        ALLOWED.put(TransactionType.INVOICE, allowedMap);

        Map<DocumentState, Map<LifecycleAction, DocumentState>> txMap =
                new EnumMap<>(DocumentState.class);
        putTransitions(txMap, DocumentState.DRAFT,
                Map.of(LifecycleAction.EDIT, DocumentState.DRAFT,
                        LifecycleAction.SUBMIT, DocumentState.SUBMITTING));
        putTransitions(txMap, DocumentState.SUBMITTING,
                Map.of(LifecycleAction.MARK_ACCEPTED, DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED, DocumentState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW, DocumentState.IN_REVIEW,
                        LifecycleAction.MARK_AMBIGUOUS, DocumentState.IN_REVIEW));
        putTransitions(txMap, DocumentState.SUBMITTED,
                Map.of(LifecycleAction.MARK_ACCEPTED, DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED, DocumentState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW, DocumentState.IN_REVIEW));
        putTransitions(txMap, DocumentState.IN_REVIEW,
                Map.of(LifecycleAction.CHECK_STATUS, DocumentState.IN_REVIEW,
                        LifecycleAction.MARK_ACCEPTED, DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED, DocumentState.REJECTED,
                        LifecycleAction.RETRY, DocumentState.SUBMITTING));
        putTransitions(txMap, DocumentState.ACCEPTED,
                Map.of(LifecycleAction.CANCEL, DocumentState.CANCELLED));
        putTransitions(txMap, DocumentState.REJECTED, Map.of());
        putTransitions(txMap, DocumentState.CANCELLED, Map.of());
        TRANSITIONS.put(TransactionType.INVOICE, txMap);
    }

    private static void initReceiptMatrix() {
        Map<DocumentState, Set<LifecycleAction>> allowedMap =
                new EnumMap<>(DocumentState.class);
        allowedMap.put(DocumentState.DRAFT, EnumSet.of(
                LifecycleAction.EDIT, LifecycleAction.DELETE,
                LifecycleAction.SUBMIT));
        allowedMap.put(DocumentState.SUBMITTING, EnumSet.of(
                LifecycleAction.MARK_ACCEPTED, LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW, LifecycleAction.MARK_AMBIGUOUS));
        allowedMap.put(DocumentState.SUBMITTED, EnumSet.of(
                LifecycleAction.MARK_ACCEPTED, LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW));
        allowedMap.put(DocumentState.IN_REVIEW, EnumSet.of(
                LifecycleAction.CHECK_STATUS, LifecycleAction.MARK_ACCEPTED,
                LifecycleAction.MARK_REJECTED, LifecycleAction.RETRY));
        allowedMap.put(DocumentState.ACCEPTED, EnumSet.of(
                LifecycleAction.CANCEL));
        allowedMap.put(DocumentState.REJECTED, EnumSet.noneOf(
                LifecycleAction.class));
        allowedMap.put(DocumentState.CANCELLED, EnumSet.noneOf(
                LifecycleAction.class));
        ALLOWED.put(TransactionType.RECEIPT, allowedMap);

        Map<DocumentState, Map<LifecycleAction, DocumentState>> txMap =
                new EnumMap<>(DocumentState.class);
        putTransitions(txMap, DocumentState.DRAFT,
                Map.of(LifecycleAction.EDIT, DocumentState.DRAFT,
                        LifecycleAction.SUBMIT, DocumentState.SUBMITTING));
        putTransitions(txMap, DocumentState.SUBMITTING,
                Map.of(LifecycleAction.MARK_ACCEPTED, DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED, DocumentState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW, DocumentState.IN_REVIEW,
                        LifecycleAction.MARK_AMBIGUOUS, DocumentState.IN_REVIEW));
        putTransitions(txMap, DocumentState.SUBMITTED,
                Map.of(LifecycleAction.MARK_ACCEPTED, DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED, DocumentState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW, DocumentState.IN_REVIEW));
        putTransitions(txMap, DocumentState.IN_REVIEW,
                Map.of(LifecycleAction.CHECK_STATUS, DocumentState.IN_REVIEW,
                        LifecycleAction.MARK_ACCEPTED, DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED, DocumentState.REJECTED,
                        LifecycleAction.RETRY, DocumentState.SUBMITTING));
        putTransitions(txMap, DocumentState.ACCEPTED,
                Map.of(LifecycleAction.CANCEL, DocumentState.CANCELLED));
        putTransitions(txMap, DocumentState.REJECTED, Map.of());
        putTransitions(txMap, DocumentState.CANCELLED, Map.of());
        TRANSITIONS.put(TransactionType.RECEIPT, txMap);
    }

    private static void initStandardMatrix() {
        Map<DocumentState, Set<LifecycleAction>> allowedMap =
                new EnumMap<>(DocumentState.class);
        allowedMap.put(DocumentState.DRAFT, EnumSet.of(
                LifecycleAction.EDIT, LifecycleAction.DELETE,
                LifecycleAction.SUBMIT));
        allowedMap.put(DocumentState.SUBMITTING, EnumSet.of(
                LifecycleAction.MARK_SUBMITTED,
                LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW));
        allowedMap.put(DocumentState.SUBMITTED, EnumSet.of(
                LifecycleAction.MARK_ACCEPTED,
                LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW));
        allowedMap.put(DocumentState.IN_REVIEW, EnumSet.of(
                LifecycleAction.CHECK_STATUS,
                LifecycleAction.MARK_ACCEPTED,
                LifecycleAction.MARK_REJECTED));
        allowedMap.put(DocumentState.ACCEPTED, EnumSet.of(
                LifecycleAction.CANCEL));
        allowedMap.put(DocumentState.REJECTED, EnumSet.noneOf(
                LifecycleAction.class));
        allowedMap.put(DocumentState.CANCELLED, EnumSet.noneOf(
                LifecycleAction.class));
        ALLOWED.put(TransactionType.STANDARD, allowedMap);

        Map<DocumentState, Map<LifecycleAction, DocumentState>> txMap =
                new EnumMap<>(DocumentState.class);
        putTransitions(txMap, DocumentState.DRAFT,
                Map.of(LifecycleAction.EDIT, DocumentState.DRAFT,
                        LifecycleAction.SUBMIT, DocumentState.SUBMITTING));
        putTransitions(txMap, DocumentState.SUBMITTING,
                Map.of(LifecycleAction.MARK_SUBMITTED,
                        DocumentState.SUBMITTED,
                        LifecycleAction.MARK_REJECTED,
                        DocumentState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW,
                        DocumentState.IN_REVIEW));
        putTransitions(txMap, DocumentState.SUBMITTED,
                Map.of(LifecycleAction.MARK_ACCEPTED,
                        DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED,
                        DocumentState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW,
                        DocumentState.IN_REVIEW));
        putTransitions(txMap, DocumentState.IN_REVIEW,
                Map.of(LifecycleAction.CHECK_STATUS,
                        DocumentState.IN_REVIEW,
                        LifecycleAction.MARK_ACCEPTED,
                        DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED,
                        DocumentState.REJECTED));
        putTransitions(txMap, DocumentState.ACCEPTED,
                Map.of(LifecycleAction.CANCEL, DocumentState.CANCELLED));
        putTransitions(txMap, DocumentState.REJECTED, Map.of());
        putTransitions(txMap, DocumentState.CANCELLED, Map.of());
        TRANSITIONS.put(TransactionType.STANDARD, txMap);
    }

    private static void initSimplifiedMatrix() {
        Map<DocumentState, Set<LifecycleAction>> allowedMap =
                new EnumMap<>(DocumentState.class);
        allowedMap.put(DocumentState.DRAFT, EnumSet.of(
                LifecycleAction.EDIT, LifecycleAction.DELETE,
                LifecycleAction.SUBMIT));
        allowedMap.put(DocumentState.SUBMITTING, EnumSet.of(
                LifecycleAction.MARK_SUBMITTED,
                LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW));
        allowedMap.put(DocumentState.SUBMITTED, EnumSet.of(
                LifecycleAction.MARK_ACCEPTED,
                LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW));
        allowedMap.put(DocumentState.IN_REVIEW, EnumSet.of(
                LifecycleAction.CHECK_STATUS,
                LifecycleAction.MARK_ACCEPTED,
                LifecycleAction.MARK_REJECTED));
        allowedMap.put(DocumentState.ACCEPTED, EnumSet.of(
                LifecycleAction.CANCEL));
        allowedMap.put(DocumentState.REJECTED, EnumSet.noneOf(
                LifecycleAction.class));
        allowedMap.put(DocumentState.CANCELLED, EnumSet.noneOf(
                LifecycleAction.class));
        ALLOWED.put(TransactionType.SIMPLIFIED, allowedMap);

        Map<DocumentState, Map<LifecycleAction, DocumentState>> txMap =
                new EnumMap<>(DocumentState.class);
        putTransitions(txMap, DocumentState.DRAFT,
                Map.of(LifecycleAction.EDIT, DocumentState.DRAFT,
                        LifecycleAction.SUBMIT, DocumentState.SUBMITTING));
        putTransitions(txMap, DocumentState.SUBMITTING,
                Map.of(LifecycleAction.MARK_SUBMITTED,
                        DocumentState.SUBMITTED,
                        LifecycleAction.MARK_REJECTED,
                        DocumentState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW,
                        DocumentState.IN_REVIEW));
        putTransitions(txMap, DocumentState.SUBMITTED,
                Map.of(LifecycleAction.MARK_ACCEPTED,
                        DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED,
                        DocumentState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW,
                        DocumentState.IN_REVIEW));
        putTransitions(txMap, DocumentState.IN_REVIEW,
                Map.of(LifecycleAction.CHECK_STATUS,
                        DocumentState.IN_REVIEW,
                        LifecycleAction.MARK_ACCEPTED,
                        DocumentState.ACCEPTED,
                        LifecycleAction.MARK_REJECTED,
                        DocumentState.REJECTED));
        putTransitions(txMap, DocumentState.ACCEPTED,
                Map.of(LifecycleAction.CANCEL, DocumentState.CANCELLED));
        putTransitions(txMap, DocumentState.REJECTED, Map.of());
        putTransitions(txMap, DocumentState.CANCELLED, Map.of());
        TRANSITIONS.put(TransactionType.SIMPLIFIED, txMap);
    }

    static void registerMatrix(TransactionType txType,
            Map<DocumentState, Set<LifecycleAction>> allowedMap,
            Map<DocumentState, Map<LifecycleAction, DocumentState>> txMap) {
        ALLOWED.put(txType, allowedMap);
        TRANSITIONS.put(txType, txMap);
    }

    private static void putTransitions(
            Map<DocumentState, Map<LifecycleAction, DocumentState>> txMap,
            DocumentState from,
            Map<LifecycleAction, DocumentState> targets) {
        EnumMap<LifecycleAction, DocumentState> copy =
                new EnumMap<>(LifecycleAction.class);
        copy.putAll(targets);
        txMap.put(from, copy);
    }
}
