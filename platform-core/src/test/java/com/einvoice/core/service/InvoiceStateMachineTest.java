package com.einvoice.core.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.exception.InvalidTransitionException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceStateMachineTest {

    @Mock
    private AuditService auditService;

    private InvoiceStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new InvoiceStateMachine(auditService);
    }

    private Invoice createInvoice(InvoiceStatus status) {
        Company company = Company.builder().build();
        company.setId(1L);
        Invoice invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .status(status)
                .company(company)
                .build();
        return invoice;
    }

    @Test
    void transition_draftToValidated_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.DRAFT);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.VALIDATED));
        assertEquals(InvoiceStatus.VALIDATED, invoice.getStatus());
    }

    @Test
    void transition_draftToCancelled_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.DRAFT);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.CANCELLED));
        assertEquals(InvoiceStatus.CANCELLED, invoice.getStatus());
    }

    @Test
    void transition_validatedToReadyForSubmission_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.VALIDATED);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.READY_FOR_SUBMISSION));
        assertEquals(InvoiceStatus.READY_FOR_SUBMISSION, invoice.getStatus());
    }

    @Test
    void transition_readyToSubmissionInProgress_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.READY_FOR_SUBMISSION);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.SUBMISSION_IN_PROGRESS));
        assertEquals(InvoiceStatus.SUBMISSION_IN_PROGRESS, invoice.getStatus());
    }

    @Test
    void transition_submissionInProgressToCleared_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.CLEARED));
        assertEquals(InvoiceStatus.CLEARED, invoice.getStatus());
    }

    @Test
    void transition_submissionInProgressToReported_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.REPORTED));
        assertEquals(InvoiceStatus.REPORTED, invoice.getStatus());
    }

    @Test
    void transition_submissionInProgressToAccepted_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.ACCEPTED));
        assertEquals(InvoiceStatus.ACCEPTED, invoice.getStatus());
    }

    @Test
    void transition_submissionInProgressToInReview_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.IN_REVIEW));
        assertEquals(InvoiceStatus.IN_REVIEW, invoice.getStatus());
    }

    @Test
    void transition_submissionInProgressToRejected_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.REJECTED));
        assertEquals(InvoiceStatus.REJECTED, invoice.getStatus());
    }

    @Test
    void transition_submissionInProgressToFailedRetryable_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.FAILED_RETRYABLE));
        assertEquals(InvoiceStatus.FAILED_RETRYABLE, invoice.getStatus());
    }

    @Test
    void transition_submissionInProgressToFailedNonRetryable_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.FAILED_NON_RETRYABLE));
        assertEquals(InvoiceStatus.FAILED_NON_RETRYABLE, invoice.getStatus());
    }

    @Test
    void transition_submissionInProgressToAmbiguous_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.SUBMISSION_AMBIGUOUS));
        assertEquals(InvoiceStatus.SUBMISSION_AMBIGUOUS, invoice.getStatus());
    }

    @Test
    void transition_rejectedToDraft_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.REJECTED);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.DRAFT));
        assertEquals(InvoiceStatus.DRAFT, invoice.getStatus());
    }

    @Test
    void transition_failedRetryableToSubmissionInProgress_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.FAILED_RETRYABLE);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.SUBMISSION_IN_PROGRESS));
        assertEquals(InvoiceStatus.SUBMISSION_IN_PROGRESS, invoice.getStatus());
    }

    @Test
    void transition_inReviewToAccepted_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.IN_REVIEW);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.ACCEPTED));
        assertEquals(InvoiceStatus.ACCEPTED, invoice.getStatus());
    }

    @Test
    void transition_inReviewToRejected_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.IN_REVIEW);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.REJECTED));
        assertEquals(InvoiceStatus.REJECTED, invoice.getStatus());
    }

    @Test
    void transition_acceptedToCancelled_succeeds() {
        Invoice invoice = createInvoice(InvoiceStatus.ACCEPTED);
        assertDoesNotThrow(() -> stateMachine.transition(invoice, InvoiceStatus.CANCELLED));
        assertEquals(InvoiceStatus.CANCELLED, invoice.getStatus());
    }

    @Test
    void transition_createsAuditLog() {
        Invoice invoice = createInvoice(InvoiceStatus.DRAFT);
        stateMachine.transition(invoice, InvoiceStatus.VALIDATED);
        verify(auditService).log(
                "STATUS_TRANSITION", "Invoice",
                invoice.getId().toString(),
                "DRAFT", "VALIDATED", 1L);
    }

    @Test
    void transition_draftToCleared_throws() {
        Invoice invoice = createInvoice(InvoiceStatus.DRAFT);
        assertThrows(InvalidTransitionException.class,
                () -> stateMachine.transition(invoice, InvoiceStatus.CLEARED));
    }

    @Test
    void transition_draftToAccepted_throws() {
        Invoice invoice = createInvoice(InvoiceStatus.DRAFT);
        assertThrows(InvalidTransitionException.class,
                () -> stateMachine.transition(invoice, InvoiceStatus.ACCEPTED));
    }

    @Test
    void transition_validatedToDraft_throws() {
        Invoice invoice = createInvoice(InvoiceStatus.VALIDATED);
        assertThrows(InvalidTransitionException.class,
                () -> stateMachine.transition(invoice, InvoiceStatus.DRAFT));
    }

    @Test
    void transition_clearedToDraft_throws() {
        Invoice invoice = createInvoice(InvoiceStatus.CLEARED);
        assertThrows(InvalidTransitionException.class,
                () -> stateMachine.transition(invoice, InvoiceStatus.DRAFT));
    }

    @Test
    void transition_cancelledToAnything_throws() {
        Invoice invoice = createInvoice(InvoiceStatus.CANCELLED);
        assertThrows(InvalidTransitionException.class,
                () -> stateMachine.transition(invoice, InvoiceStatus.DRAFT));
    }

    @Test
    void transition_sameState_throws() {
        Invoice invoice = createInvoice(InvoiceStatus.DRAFT);
        assertThrows(InvalidTransitionException.class,
                () -> stateMachine.transition(invoice, InvoiceStatus.DRAFT));
    }

    @Test
    void canTransition_validTransition_returnsTrue() {
        assertTrue(stateMachine.canTransition(InvoiceStatus.DRAFT, InvoiceStatus.VALIDATED));
    }

    @Test
    void canTransition_invalidTransition_returnsFalse() {
        assertFalse(stateMachine.canTransition(InvoiceStatus.DRAFT, InvoiceStatus.CLEARED));
    }

    @Test
    void getAllowedTransitions_returnsCorrectSet() {
        Set<InvoiceStatus> allowed = stateMachine.getAllowedTransitions(InvoiceStatus.SUBMISSION_IN_PROGRESS);
        assertEquals(8, allowed.size());
        assertTrue(allowed.contains(InvoiceStatus.CLEARED));
        assertTrue(allowed.contains(InvoiceStatus.REPORTED));
        assertTrue(allowed.contains(InvoiceStatus.ACCEPTED));
        assertTrue(allowed.contains(InvoiceStatus.IN_REVIEW));
        assertTrue(allowed.contains(InvoiceStatus.REJECTED));
        assertTrue(allowed.contains(InvoiceStatus.FAILED_RETRYABLE));
        assertTrue(allowed.contains(InvoiceStatus.FAILED_NON_RETRYABLE));
        assertTrue(allowed.contains(InvoiceStatus.SUBMISSION_AMBIGUOUS));
    }

    @Test
    void getAllowedTransitions_terminalState_returnsEmpty() {
        Set<InvoiceStatus> allowed = stateMachine.getAllowedTransitions(InvoiceStatus.CANCELLED);
        assertTrue(allowed.isEmpty());
    }
}
