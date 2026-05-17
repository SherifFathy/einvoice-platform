package com.einvoice.core.lifecycle;

import com.einvoice.core.domain.eta.lifecycle.EtaReceiptState;
import com.einvoice.core.domain.eta.lifecycle.LifecycleAction;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Javadoc. */
public final class EtaReceiptLifecycle {

    private static final Map<EtaReceiptState, Set<LifecycleAction>> ALLOWED;
    private static final Map<EtaReceiptState, Map<LifecycleAction, EtaReceiptState>> TRANSITIONS;

    static {
        ALLOWED = new EnumMap<>(EtaReceiptState.class);
        ALLOWED.put(EtaReceiptState.DRAFT, EnumSet.of(
                LifecycleAction.EDIT, LifecycleAction.DELETE, LifecycleAction.SUBMIT));
        ALLOWED.put(EtaReceiptState.SUBMITTING, EnumSet.of(
                LifecycleAction.MARK_VALID, LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW, LifecycleAction.MARK_AMBIGUOUS));
        ALLOWED.put(EtaReceiptState.IN_REVIEW, EnumSet.of(
                LifecycleAction.CHECK_STATUS, LifecycleAction.MARK_VALID, LifecycleAction.MARK_REJECTED));
        ALLOWED.put(EtaReceiptState.VALID, EnumSet.of(
                LifecycleAction.CANCEL));
        ALLOWED.put(EtaReceiptState.REJECTED, EnumSet.noneOf(LifecycleAction.class));
        ALLOWED.put(EtaReceiptState.SUBMISSION_AMBIGUOUS, EnumSet.of(
                LifecycleAction.RETRY));
        ALLOWED.put(EtaReceiptState.CANCELLED, EnumSet.noneOf(LifecycleAction.class));

        TRANSITIONS = new EnumMap<>(EtaReceiptState.class);
        addTransitions(EtaReceiptState.DRAFT,
                Map.of(
                        LifecycleAction.EDIT, EtaReceiptState.DRAFT,
                        LifecycleAction.SUBMIT, EtaReceiptState.SUBMITTING));
        addTransitions(EtaReceiptState.SUBMITTING,
                Map.of(
                        LifecycleAction.MARK_VALID, EtaReceiptState.VALID,
                        LifecycleAction.MARK_REJECTED, EtaReceiptState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW, EtaReceiptState.IN_REVIEW,
                        LifecycleAction.MARK_AMBIGUOUS, EtaReceiptState.SUBMISSION_AMBIGUOUS));
        addTransitions(EtaReceiptState.IN_REVIEW,
                Map.of(
                        LifecycleAction.CHECK_STATUS, EtaReceiptState.IN_REVIEW,
                        LifecycleAction.MARK_VALID, EtaReceiptState.VALID,
                        LifecycleAction.MARK_REJECTED, EtaReceiptState.REJECTED));
        addTransitions(EtaReceiptState.VALID,
                Map.of(
                        LifecycleAction.CANCEL, EtaReceiptState.CANCELLED));
        addTransitions(EtaReceiptState.SUBMISSION_AMBIGUOUS,
                Map.of(
                        LifecycleAction.RETRY, EtaReceiptState.SUBMITTING));
        addTransitions(EtaReceiptState.CANCELLED, Map.of());
        addTransitions(EtaReceiptState.REJECTED, Map.of());
    }

    private EtaReceiptLifecycle() {
    }

    private static void addTransitions(EtaReceiptState from,
            Map<LifecycleAction, EtaReceiptState> targets) {
        EnumMap<LifecycleAction, EtaReceiptState> copy = new EnumMap<>(LifecycleAction.class);
        copy.putAll(targets);
        TRANSITIONS.put(from, copy);
    }

    public static boolean allowed(EtaReceiptState from, LifecycleAction action) {
        Set<LifecycleAction> actions = ALLOWED.get(from);
        return actions != null && actions.contains(action);
    }

    /**
     * Compute the next state for a given transition.
     *
     * @param from   the originating state
     * @param action the lifecycle action
     * @return the next state, or null if the action does not transition
     */
    public static EtaReceiptState next(EtaReceiptState from, LifecycleAction action) {
        if (!allowed(from, action)) {
            throw new InvalidLifecycleTransitionException(
                    "Transition not allowed: " + from + " + " + action,
                    from.name(), action.name());
        }
        if (action == LifecycleAction.DELETE) {
            return null;
        }
        Map<LifecycleAction, EtaReceiptState> targets = TRANSITIONS.get(from);
        if (targets == null || !targets.containsKey(action)) {
            throw new InvalidLifecycleTransitionException(
                    "No target state defined for: " + from + " + " + action,
                    from.name(), action.name());
        }
        return targets.get(action);
    }
}
