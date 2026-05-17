package com.einvoice.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.domain.eta.lifecycle.LifecycleAction;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EtaInvoiceLifecycleTest {

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(EtaInvoiceState.DRAFT, LifecycleAction.EDIT, EtaInvoiceState.DRAFT),
                Arguments.of(EtaInvoiceState.DRAFT, LifecycleAction.SUBMIT, EtaInvoiceState.SUBMITTING),
                Arguments.of(EtaInvoiceState.SUBMITTING, LifecycleAction.MARK_VALID, EtaInvoiceState.VALID),
                Arguments.of(EtaInvoiceState.SUBMITTING, LifecycleAction.MARK_REJECTED, EtaInvoiceState.REJECTED),
                Arguments.of(EtaInvoiceState.SUBMITTING, LifecycleAction.MARK_IN_REVIEW, EtaInvoiceState.IN_REVIEW),
                Arguments.of(EtaInvoiceState.SUBMITTING,
                        LifecycleAction.MARK_AMBIGUOUS,
                        EtaInvoiceState.SUBMISSION_AMBIGUOUS),
                Arguments.of(EtaInvoiceState.IN_REVIEW, LifecycleAction.MARK_VALID, EtaInvoiceState.VALID),
                Arguments.of(EtaInvoiceState.IN_REVIEW, LifecycleAction.MARK_REJECTED, EtaInvoiceState.REJECTED),
                Arguments.of(EtaInvoiceState.VALID, LifecycleAction.CANCEL, EtaInvoiceState.CANCELLED),
                Arguments.of(EtaInvoiceState.SUBMISSION_AMBIGUOUS, LifecycleAction.RETRY, EtaInvoiceState.SUBMITTING)
        );
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void allowed_returnsTrue_forValidTransition(EtaInvoiceState from, LifecycleAction action, EtaInvoiceState target) {
        assertTrue(EtaInvoiceLifecycle.allowed(from, action));
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void next_returnsCorrectTarget_forValidTransition(
            EtaInvoiceState from, LifecycleAction action, EtaInvoiceState expected) {
        EtaInvoiceState result = EtaInvoiceLifecycle.next(from, action);
        assertEquals(expected, result);
    }

    @Test
    void delete_fromDraft_isAllowed_andReturnsNull() {
        assertTrue(EtaInvoiceLifecycle.allowed(EtaInvoiceState.DRAFT, LifecycleAction.DELETE));
        assertNull(EtaInvoiceLifecycle.next(EtaInvoiceState.DRAFT, LifecycleAction.DELETE));
    }

    @Test
    void checkStatus_fromInReview_isAllowed() {
        assertTrue(EtaInvoiceLifecycle.allowed(EtaInvoiceState.IN_REVIEW, LifecycleAction.CHECK_STATUS));
    }

    static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(EtaInvoiceState.REJECTED, LifecycleAction.EDIT),
                Arguments.of(EtaInvoiceState.REJECTED, LifecycleAction.DELETE),
                Arguments.of(EtaInvoiceState.REJECTED, LifecycleAction.SUBMIT),
                Arguments.of(EtaInvoiceState.REJECTED, LifecycleAction.CANCEL),
                Arguments.of(EtaInvoiceState.REJECTED, LifecycleAction.RETRY),
                Arguments.of(EtaInvoiceState.REJECTED, LifecycleAction.MARK_VALID),
                Arguments.of(EtaInvoiceState.REJECTED, LifecycleAction.CHECK_STATUS),
                Arguments.of(EtaInvoiceState.REJECTED, LifecycleAction.CLONE_TO_NEW_DRAFT),
                Arguments.of(EtaInvoiceState.CANCELLED, LifecycleAction.EDIT),
                Arguments.of(EtaInvoiceState.CANCELLED, LifecycleAction.DELETE),
                Arguments.of(EtaInvoiceState.CANCELLED, LifecycleAction.SUBMIT),
                Arguments.of(EtaInvoiceState.CANCELLED, LifecycleAction.CANCEL),
                Arguments.of(EtaInvoiceState.CANCELLED, LifecycleAction.RETRY),
                Arguments.of(EtaInvoiceState.CANCELLED, LifecycleAction.CLONE_TO_NEW_DRAFT),
                Arguments.of(EtaInvoiceState.CANCELLED, LifecycleAction.MARK_VALID),
                Arguments.of(EtaInvoiceState.CANCELLED, LifecycleAction.CHECK_STATUS),
                Arguments.of(EtaInvoiceState.VALID, LifecycleAction.EDIT),
                Arguments.of(EtaInvoiceState.VALID, LifecycleAction.DELETE),
                Arguments.of(EtaInvoiceState.VALID, LifecycleAction.SUBMIT),
                Arguments.of(EtaInvoiceState.VALID, LifecycleAction.RETRY),
                Arguments.of(EtaInvoiceState.DRAFT, LifecycleAction.CANCEL),
                Arguments.of(EtaInvoiceState.DRAFT, LifecycleAction.RETRY),
                Arguments.of(EtaInvoiceState.DRAFT, LifecycleAction.MARK_VALID),
                Arguments.of(EtaInvoiceState.DRAFT, LifecycleAction.CHECK_STATUS),
                Arguments.of(EtaInvoiceState.SUBMITTING, LifecycleAction.EDIT),
                Arguments.of(EtaInvoiceState.SUBMITTING, LifecycleAction.SUBMIT),
                Arguments.of(EtaInvoiceState.SUBMITTING, LifecycleAction.CHECK_STATUS)
        );
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void allowed_returnsFalse_forDisallowedTransition(EtaInvoiceState from, LifecycleAction action) {
        assertFalse(EtaInvoiceLifecycle.allowed(from, action));
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void next_throws_forDisallowedTransition(EtaInvoiceState from, LifecycleAction action) {
        assertThrows(InvalidLifecycleTransitionException.class,
                () -> EtaInvoiceLifecycle.next(from, action));
    }

    @Test
    void rejected_hasNoOutgoingTransitions() {
        for (LifecycleAction action : LifecycleAction.values()) {
            assertFalse(EtaInvoiceLifecycle.allowed(EtaInvoiceState.REJECTED, action),
                    "REJECTED should not allow " + action);
        }
    }

    @Test
    void cancelled_hasNoOutgoingTransitions() {
        for (LifecycleAction action : LifecycleAction.values()) {
            assertFalse(EtaInvoiceLifecycle.allowed(EtaInvoiceState.CANCELLED, action),
                    "CANCELLED should not allow " + action);
        }
    }
}
