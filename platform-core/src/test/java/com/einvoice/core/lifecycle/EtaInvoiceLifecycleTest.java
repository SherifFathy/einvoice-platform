package com.einvoice.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.LifecycleAction;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EtaInvoiceLifecycleTest {

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(DocumentState.DRAFT, LifecycleAction.EDIT, DocumentState.DRAFT),
                Arguments.of(DocumentState.DRAFT, LifecycleAction.SUBMIT, DocumentState.SUBMITTING),
                Arguments.of(DocumentState.SUBMITTING, LifecycleAction.MARK_ACCEPTED, DocumentState.ACCEPTED),
                Arguments.of(DocumentState.SUBMITTING, LifecycleAction.MARK_REJECTED, DocumentState.REJECTED),
                Arguments.of(DocumentState.SUBMITTING, LifecycleAction.MARK_IN_REVIEW, DocumentState.IN_REVIEW),
                Arguments.of(DocumentState.SUBMITTING,
                        LifecycleAction.MARK_AMBIGUOUS,
                        DocumentState.IN_REVIEW),
                Arguments.of(DocumentState.IN_REVIEW, LifecycleAction.MARK_ACCEPTED, DocumentState.ACCEPTED),
                Arguments.of(DocumentState.IN_REVIEW, LifecycleAction.MARK_REJECTED, DocumentState.REJECTED),
                Arguments.of(DocumentState.ACCEPTED, LifecycleAction.CANCEL, DocumentState.CANCELLED),
                Arguments.of(DocumentState.IN_REVIEW, LifecycleAction.RETRY, DocumentState.SUBMITTING)
        );
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void allowed_returnsTrue_forValidTransition(DocumentState from, LifecycleAction action, DocumentState target) {
        assertTrue(EtaInvoiceLifecycle.allowed(from, action));
    }

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void next_returnsCorrectTarget_forValidTransition(
            DocumentState from, LifecycleAction action, DocumentState expected) {
        DocumentState result = EtaInvoiceLifecycle.next(from, action);
        assertEquals(expected, result);
    }

    @Test
    void delete_fromDraft_isAllowed_andReturnsNull() {
        assertTrue(EtaInvoiceLifecycle.allowed(DocumentState.DRAFT, LifecycleAction.DELETE));
        assertNull(EtaInvoiceLifecycle.next(DocumentState.DRAFT, LifecycleAction.DELETE));
    }

    @Test
    void checkStatus_fromInReview_isAllowed() {
        assertTrue(EtaInvoiceLifecycle.allowed(DocumentState.IN_REVIEW, LifecycleAction.CHECK_STATUS));
    }

    static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                Arguments.of(DocumentState.REJECTED, LifecycleAction.EDIT),
                Arguments.of(DocumentState.REJECTED, LifecycleAction.DELETE),
                Arguments.of(DocumentState.REJECTED, LifecycleAction.SUBMIT),
                Arguments.of(DocumentState.REJECTED, LifecycleAction.CANCEL),
                Arguments.of(DocumentState.REJECTED, LifecycleAction.RETRY),
                Arguments.of(DocumentState.REJECTED, LifecycleAction.MARK_ACCEPTED),
                Arguments.of(DocumentState.REJECTED, LifecycleAction.CHECK_STATUS),
                Arguments.of(DocumentState.REJECTED, LifecycleAction.CLONE_TO_NEW_DRAFT),
                Arguments.of(DocumentState.CANCELLED, LifecycleAction.EDIT),
                Arguments.of(DocumentState.CANCELLED, LifecycleAction.DELETE),
                Arguments.of(DocumentState.CANCELLED, LifecycleAction.SUBMIT),
                Arguments.of(DocumentState.CANCELLED, LifecycleAction.CANCEL),
                Arguments.of(DocumentState.CANCELLED, LifecycleAction.RETRY),
                Arguments.of(DocumentState.CANCELLED, LifecycleAction.CLONE_TO_NEW_DRAFT),
                Arguments.of(DocumentState.CANCELLED, LifecycleAction.MARK_ACCEPTED),
                Arguments.of(DocumentState.CANCELLED, LifecycleAction.CHECK_STATUS),
                Arguments.of(DocumentState.ACCEPTED, LifecycleAction.EDIT),
                Arguments.of(DocumentState.ACCEPTED, LifecycleAction.DELETE),
                Arguments.of(DocumentState.ACCEPTED, LifecycleAction.SUBMIT),
                Arguments.of(DocumentState.ACCEPTED, LifecycleAction.RETRY),
                Arguments.of(DocumentState.DRAFT, LifecycleAction.CANCEL),
                Arguments.of(DocumentState.DRAFT, LifecycleAction.RETRY),
                Arguments.of(DocumentState.DRAFT, LifecycleAction.MARK_ACCEPTED),
                Arguments.of(DocumentState.DRAFT, LifecycleAction.CHECK_STATUS),
                Arguments.of(DocumentState.SUBMITTING, LifecycleAction.EDIT),
                Arguments.of(DocumentState.SUBMITTING, LifecycleAction.SUBMIT),
                Arguments.of(DocumentState.SUBMITTING, LifecycleAction.CHECK_STATUS)
        );
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void allowed_returnsFalse_forDisallowedTransition(DocumentState from, LifecycleAction action) {
        assertFalse(EtaInvoiceLifecycle.allowed(from, action));
    }

    @ParameterizedTest
    @MethodSource("disallowedTransitions")
    void next_throws_forDisallowedTransition(DocumentState from, LifecycleAction action) {
        assertThrows(InvalidLifecycleTransitionException.class,
                () -> EtaInvoiceLifecycle.next(from, action));
    }

    @Test
    void rejected_hasNoOutgoingTransitions() {
        for (LifecycleAction action : LifecycleAction.values()) {
            assertFalse(EtaInvoiceLifecycle.allowed(DocumentState.REJECTED, action),
                    "REJECTED should not allow " + action);
        }
    }

    @Test
    void cancelled_hasNoOutgoingTransitions() {
        for (LifecycleAction action : LifecycleAction.values()) {
            assertFalse(EtaInvoiceLifecycle.allowed(DocumentState.CANCELLED, action),
                    "CANCELLED should not allow " + action);
        }
    }
}
