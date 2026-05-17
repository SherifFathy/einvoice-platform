package com.einvoice.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.eta.lifecycle.EtaReceiptState;
import com.einvoice.core.domain.eta.lifecycle.LifecycleAction;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EtaReceiptLifecycleTest {

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(EtaReceiptState.DRAFT, LifecycleAction.EDIT, EtaReceiptState.DRAFT),
                Arguments.of(EtaReceiptState.DRAFT, LifecycleAction.SUBMIT,
                        EtaReceiptState.SUBMITTING),
                Arguments.of(EtaReceiptState.SUBMITTING, LifecycleAction.MARK_VALID,
                        EtaReceiptState.VALID),
                Arguments.of(EtaReceiptState.SUBMITTING, LifecycleAction.MARK_REJECTED,
                        EtaReceiptState.REJECTED),
                Arguments.of(EtaReceiptState.SUBMITTING, LifecycleAction.MARK_IN_REVIEW,
                        EtaReceiptState.IN_REVIEW),
                Arguments.of(EtaReceiptState.SUBMITTING,
                        LifecycleAction.MARK_AMBIGUOUS,
                        EtaReceiptState.SUBMISSION_AMBIGUOUS),
                Arguments.of(EtaReceiptState.IN_REVIEW, LifecycleAction.MARK_VALID,
                        EtaReceiptState.VALID),
                Arguments.of(EtaReceiptState.IN_REVIEW, LifecycleAction.MARK_REJECTED,
                        EtaReceiptState.REJECTED),
                Arguments.of(EtaReceiptState.VALID, LifecycleAction.CANCEL,
                        EtaReceiptState.CANCELLED),
                Arguments.of(EtaReceiptState.SUBMISSION_AMBIGUOUS, LifecycleAction.RETRY,
                        EtaReceiptState.SUBMITTING)
        );
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void allowed_returnsTrue_forValidTransition(
            EtaReceiptState from, LifecycleAction action, EtaReceiptState target) {
        assertTrue(EtaReceiptLifecycle.allowed(from, action));
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void next_returnsCorrectTarget_forValidTransition(
            EtaReceiptState from, LifecycleAction action, EtaReceiptState expected) {
        assertEquals(expected, EtaReceiptLifecycle.next(from, action));
    }

    @Test
    void delete_fromDraft_isAllowed_andReturnsNull() {
        assertTrue(EtaReceiptLifecycle.allowed(EtaReceiptState.DRAFT, LifecycleAction.DELETE));
        assertNull(EtaReceiptLifecycle.next(EtaReceiptState.DRAFT, LifecycleAction.DELETE));
    }

    @Test
    void checkStatus_fromInReview_isAllowed() {
        assertTrue(EtaReceiptLifecycle.allowed(
                EtaReceiptState.IN_REVIEW, LifecycleAction.CHECK_STATUS));
    }

    @Test
    void rejected_hasNoOutgoingTransitions() {
        for (LifecycleAction action : LifecycleAction.values()) {
            assertFalse(EtaReceiptLifecycle.allowed(EtaReceiptState.REJECTED, action),
                    "REJECTED should not allow " + action);
        }
    }

    @Test
    void cancelled_hasNoOutgoingTransitions() {
        for (LifecycleAction action : LifecycleAction.values()) {
            assertFalse(EtaReceiptLifecycle.allowed(EtaReceiptState.CANCELLED, action),
                    "CANCELLED should not allow " + action);
        }
    }

    static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(EtaReceiptState.REJECTED, LifecycleAction.EDIT),
                Arguments.of(EtaReceiptState.REJECTED, LifecycleAction.DELETE),
                Arguments.of(EtaReceiptState.REJECTED, LifecycleAction.SUBMIT),
                Arguments.of(EtaReceiptState.REJECTED, LifecycleAction.CANCEL),
                Arguments.of(EtaReceiptState.REJECTED, LifecycleAction.RETRY),
                Arguments.of(EtaReceiptState.REJECTED, LifecycleAction.MARK_VALID),
                Arguments.of(EtaReceiptState.REJECTED, LifecycleAction.CHECK_STATUS),
                Arguments.of(EtaReceiptState.REJECTED, LifecycleAction.CLONE_TO_NEW_DRAFT),
                Arguments.of(EtaReceiptState.CANCELLED, LifecycleAction.EDIT),
                Arguments.of(EtaReceiptState.CANCELLED, LifecycleAction.DELETE),
                Arguments.of(EtaReceiptState.CANCELLED, LifecycleAction.SUBMIT),
                Arguments.of(EtaReceiptState.CANCELLED, LifecycleAction.CANCEL),
                Arguments.of(EtaReceiptState.CANCELLED, LifecycleAction.RETRY),
                Arguments.of(EtaReceiptState.CANCELLED, LifecycleAction.CLONE_TO_NEW_DRAFT),
                Arguments.of(EtaReceiptState.CANCELLED, LifecycleAction.MARK_VALID),
                Arguments.of(EtaReceiptState.CANCELLED, LifecycleAction.CHECK_STATUS),
                Arguments.of(EtaReceiptState.VALID, LifecycleAction.EDIT),
                Arguments.of(EtaReceiptState.VALID, LifecycleAction.DELETE),
                Arguments.of(EtaReceiptState.VALID, LifecycleAction.SUBMIT),
                Arguments.of(EtaReceiptState.VALID, LifecycleAction.RETRY),
                Arguments.of(EtaReceiptState.DRAFT, LifecycleAction.CANCEL),
                Arguments.of(EtaReceiptState.DRAFT, LifecycleAction.RETRY),
                Arguments.of(EtaReceiptState.DRAFT, LifecycleAction.MARK_VALID),
                Arguments.of(EtaReceiptState.DRAFT, LifecycleAction.CHECK_STATUS),
                Arguments.of(EtaReceiptState.SUBMITTING, LifecycleAction.EDIT),
                Arguments.of(EtaReceiptState.SUBMITTING, LifecycleAction.SUBMIT),
                Arguments.of(EtaReceiptState.SUBMITTING, LifecycleAction.CHECK_STATUS)
        );
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void allowed_returnsFalse_forDisallowedTransition(
            EtaReceiptState from, LifecycleAction action) {
        assertFalse(EtaReceiptLifecycle.allowed(from, action));
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void next_throws_forDisallowedTransition(
            EtaReceiptState from, LifecycleAction action) {
        assertThrows(InvalidLifecycleTransitionException.class,
                () -> EtaReceiptLifecycle.next(from, action));
    }
}
