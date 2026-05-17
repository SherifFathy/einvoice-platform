package com.einvoice.core.lifecycle;

import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.domain.eta.lifecycle.LifecycleAction;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Javadoc. */
public final class EtaInvoiceLifecycle {

    private static final Map<EtaInvoiceState, Set<LifecycleAction>> ALLOWED;
    private static final Map<EtaInvoiceState, Map<LifecycleAction, EtaInvoiceState>> TRANSITIONS;

    static {
        ALLOWED = new EnumMap<>(EtaInvoiceState.class);
        ALLOWED.put(EtaInvoiceState.DRAFT, EnumSet.of(
                LifecycleAction.EDIT, LifecycleAction.DELETE, LifecycleAction.SUBMIT));
        ALLOWED.put(EtaInvoiceState.SUBMITTING, EnumSet.of(
                LifecycleAction.MARK_VALID, LifecycleAction.MARK_REJECTED,
                LifecycleAction.MARK_IN_REVIEW, LifecycleAction.MARK_AMBIGUOUS));
        ALLOWED.put(EtaInvoiceState.IN_REVIEW, EnumSet.of(
                LifecycleAction.CHECK_STATUS, LifecycleAction.MARK_VALID, LifecycleAction.MARK_REJECTED));
        ALLOWED.put(EtaInvoiceState.VALID, EnumSet.of(
                LifecycleAction.CANCEL));
        ALLOWED.put(EtaInvoiceState.REJECTED, EnumSet.noneOf(LifecycleAction.class));
        ALLOWED.put(EtaInvoiceState.SUBMISSION_AMBIGUOUS, EnumSet.of(
                LifecycleAction.RETRY));
        ALLOWED.put(EtaInvoiceState.CANCELLED, EnumSet.noneOf(LifecycleAction.class));

        TRANSITIONS = new EnumMap<>(EtaInvoiceState.class);
        addTransitions(EtaInvoiceState.DRAFT,
                Map.of(
                        LifecycleAction.EDIT, EtaInvoiceState.DRAFT,
                        LifecycleAction.SUBMIT, EtaInvoiceState.SUBMITTING));
        addTransitions(EtaInvoiceState.SUBMITTING,
                Map.of(
                        LifecycleAction.MARK_VALID, EtaInvoiceState.VALID,
                        LifecycleAction.MARK_REJECTED, EtaInvoiceState.REJECTED,
                        LifecycleAction.MARK_IN_REVIEW, EtaInvoiceState.IN_REVIEW,
                        LifecycleAction.MARK_AMBIGUOUS, EtaInvoiceState.SUBMISSION_AMBIGUOUS));
        addTransitions(EtaInvoiceState.IN_REVIEW,
                Map.of(
                        LifecycleAction.CHECK_STATUS, EtaInvoiceState.IN_REVIEW,
                        LifecycleAction.MARK_VALID, EtaInvoiceState.VALID,
                        LifecycleAction.MARK_REJECTED, EtaInvoiceState.REJECTED));
        addTransitions(EtaInvoiceState.VALID,
                Map.of(
                        LifecycleAction.CANCEL, EtaInvoiceState.CANCELLED));
        addTransitions(EtaInvoiceState.SUBMISSION_AMBIGUOUS,
                Map.of(
                        LifecycleAction.RETRY, EtaInvoiceState.SUBMITTING));
        addTransitions(EtaInvoiceState.CANCELLED, Map.of());
        addTransitions(EtaInvoiceState.REJECTED, Map.of());
    }

    private EtaInvoiceLifecycle() {
    }

    private static void addTransitions(EtaInvoiceState from,
            Map<LifecycleAction, EtaInvoiceState> targets) {
        EnumMap<LifecycleAction, EtaInvoiceState> copy = new EnumMap<>(LifecycleAction.class);
        copy.putAll(targets);
        TRANSITIONS.put(from, copy);
    }

    public static boolean allowed(EtaInvoiceState from, LifecycleAction action) {
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
    public static EtaInvoiceState next(EtaInvoiceState from, LifecycleAction action) {
        if (!allowed(from, action)) {
            throw new InvalidLifecycleTransitionException(
                    "Transition not allowed: " + from + " + " + action,
                    from.name(), action.name());
        }
        if (action == LifecycleAction.DELETE) {
            return null;
        }
        Map<LifecycleAction, EtaInvoiceState> targets = TRANSITIONS.get(from);
        if (targets == null || !targets.containsKey(action)) {
            throw new InvalidLifecycleTransitionException(
                    "No target state defined for: " + from + " + " + action,
                    from.name(), action.name());
        }
        return targets.get(action);
    }
}
