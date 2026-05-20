package com.einvoice.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.LifecycleAction;
import com.einvoice.core.domain.shared.LifecycleTransitions;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import org.junit.jupiter.api.Test;

class LifecycleTransitionsTest {

    @Test
    void standardDraftSubmitGoesToSubmitting() {
        DocumentState result = LifecycleTransitions.next(
                DocumentState.DRAFT, LifecycleAction.SUBMIT, TransactionType.STANDARD);
        assertEquals(DocumentState.SUBMITTING, result);
    }

    @Test
    void standardSubmittingMarkSubmittedGoesToSubmitted() {
        DocumentState result = LifecycleTransitions.next(
                DocumentState.SUBMITTING, LifecycleAction.MARK_SUBMITTED, TransactionType.STANDARD);
        assertEquals(DocumentState.SUBMITTED, result);
    }

    @Test
    void standardSubmittedMarkAcceptedGoesToAccepted() {
        DocumentState result = LifecycleTransitions.next(
                DocumentState.SUBMITTED, LifecycleAction.MARK_ACCEPTED, TransactionType.STANDARD);
        assertEquals(DocumentState.ACCEPTED, result);
    }

    @Test
    void standardAcceptedCancelGoesToCancelled() {
        DocumentState result = LifecycleTransitions.next(
                DocumentState.ACCEPTED, LifecycleAction.CANCEL, TransactionType.STANDARD);
        assertEquals(DocumentState.CANCELLED, result);
    }

    @Test
    void standardRejectedHasNoOutgoingEdges() {
        for (LifecycleAction action : LifecycleAction.values()) {
            assertFalse(LifecycleTransitions.allowed(
                    DocumentState.REJECTED, action, TransactionType.STANDARD),
                    "REJECTED should not allow " + action);
        }
    }

    @Test
    void standardCancelledHasNoOutgoingEdges() {
        for (LifecycleAction action : LifecycleAction.values()) {
            assertFalse(LifecycleTransitions.allowed(
                    DocumentState.CANCELLED, action, TransactionType.STANDARD),
                    "CANCELLED should not allow " + action);
        }
    }

    @Test
    void standardInvalidTransitionThrows() {
        assertThrows(InvalidLifecycleTransitionException.class, () ->
                LifecycleTransitions.next(
                        DocumentState.ACCEPTED, LifecycleAction.SUBMIT, TransactionType.STANDARD));
    }

    @Test
    void simplifiedMatchesStandard() {
        for (DocumentState state : DocumentState.values()) {
            for (LifecycleAction action : LifecycleAction.values()) {
                boolean stdAllowed = LifecycleTransitions.allowed(state, action, TransactionType.STANDARD);
                boolean simpAllowed = LifecycleTransitions.allowed(state, action, TransactionType.SIMPLIFIED);
                assertEquals(stdAllowed, simpAllowed,
                        "Simplified should match Standard for " + state + " + " + action);

                if (stdAllowed && action != LifecycleAction.DELETE) {
                    DocumentState stdNext = LifecycleTransitions.next(state, action, TransactionType.STANDARD);
                    DocumentState simpNext = LifecycleTransitions.next(state, action, TransactionType.SIMPLIFIED);
                    assertEquals(stdNext, simpNext,
                            "Simplified next should match Standard for " + state + " + " + action);
                }
            }
        }
    }

    @Test
    void invoiceRetryFromInReview() {
        assertTrue(LifecycleTransitions.allowed(
                DocumentState.IN_REVIEW, LifecycleAction.RETRY, TransactionType.INVOICE));
        DocumentState result = LifecycleTransitions.next(
                DocumentState.IN_REVIEW, LifecycleAction.RETRY, TransactionType.INVOICE);
        assertEquals(DocumentState.SUBMITTING, result);
    }

    @Test
    void standardNoRetryFromInReview() {
        assertFalse(LifecycleTransitions.allowed(
                DocumentState.IN_REVIEW, LifecycleAction.RETRY, TransactionType.STANDARD));
    }

    @Test
    void standardSubmittingCannotGoDirectlyToAccepted() {
        assertFalse(LifecycleTransitions.allowed(
                DocumentState.SUBMITTING, LifecycleAction.MARK_ACCEPTED,
                TransactionType.STANDARD));
    }

    @Test
    void standardSubmittingMarkAcceptedThrows() {
        assertThrows(InvalidLifecycleTransitionException.class, () ->
                LifecycleTransitions.next(
                        DocumentState.SUBMITTING,
                        LifecycleAction.MARK_ACCEPTED,
                        TransactionType.STANDARD));
    }

    @Test
    void simplifiedSubmittingCannotGoDirectlyToAccepted() {
        assertFalse(LifecycleTransitions.allowed(
                DocumentState.SUBMITTING, LifecycleAction.MARK_ACCEPTED,
                TransactionType.SIMPLIFIED));
    }
}
