package com.einvoice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.SubmissionAttempt;
import com.einvoice.core.domain.enums.ArtifactType;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.domain.enums.SubmissionResult;
import com.einvoice.core.exception.InvalidTransitionException;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.InvoiceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionOrchestratorTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AuthorityConfigRepository authorityConfigRepository;
    @Mock private InvoiceStateMachine stateMachine;
    @Mock private AuthorityEngineFactory engineFactory;
    @Mock private AuthorityEngine engine;
    @Mock private InvoiceArtifactService artifactService;
    @Mock private SubmissionAttemptService attemptService;
    @Mock private AuditService auditService;
    @Mock private SubmissionAttempt mockAttempt;

    private SubmissionOrchestrator orchestrator;

    private UUID invoiceId;
    private Company company;
    private Branch branch;
    private Invoice invoice;
    private AuthorityConfig config;

    @BeforeEach
    void setUp() {
        orchestrator = new SubmissionOrchestrator(
                invoiceRepository, authorityConfigRepository,
                stateMachine, engineFactory, artifactService,
                attemptService, auditService);

        invoiceId = UUID.randomUUID();
        company = Company.builder().build();
        company.setId(1L);
        branch = Branch.builder().build();
        branch.setId(100L);

        invoice = Invoice.builder()
                .id(invoiceId)
                .company(company)
                .branch(branch)
                .authority(Authority.ZATCA)
                .type(InvoiceType.TAX_INVOICE)
                .environment(Environment.ZATCA_SANDBOX)
                .status(InvoiceStatus.READY_FOR_SUBMISSION)
                .issueDate(java.time.LocalDate.now())
                .build();

        config = AuthorityConfig.builder()
                .branch(branch)
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .invoiceCounter(0L)
                .previousInvoiceHash("seed-hash")
                .build();

        when(engineFactory.getEngine(Authority.ZATCA)).thenReturn(engine);
        when(engine.getPayloadArtifactType()).thenReturn(ArtifactType.SIGNED_XML);
        when(engine.getResponseArtifactType()).thenReturn(ArtifactType.ZATCA_RESPONSE);
        when(engine.getClearedArtifactType()).thenReturn(ArtifactType.CLEARED_XML);
    }

    private void stubCommonMocks() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(authorityConfigRepository.findWithLockByBranchIdAndAuthorityAndEnvironment(
                branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(Optional.of(config));
        when(attemptService.getNextAttemptNumber(invoiceId)).thenReturn(1);
        when(attemptService.createAttempt(any(), eq(1), eq(config)))
                .thenReturn(mockAttempt);
        when(engine.generatePayload(any(), any())).thenReturn("<Invoice/>");
        when(engine.computeInvoiceHash("<Invoice/>")).thenReturn("hash123");
    }

    @Test
    void submit_success_persistsArtifactBeforeSubmitting() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success(
                        "<cleared/>", "<response/>"));

        orchestrator.submit(invoiceId);

        InOrder inOrder = inOrder(artifactService, engine);
        inOrder.verify(artifactService).storeArtifact(
                any(), eq(ArtifactType.SIGNED_XML), eq("<Invoice/>"));
        inOrder.verify(engine).submit(any(), eq("<Invoice/>"), eq(config));
    }

    @Test
    void submit_success_updatesHashChain() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success(
                        "<cleared/>", "<response/>"));

        orchestrator.submit(invoiceId);

        assertEquals("hash123", config.getPreviousInvoiceHash());
        assertEquals(1L, config.getInvoiceCounter());
        verify(authorityConfigRepository).save(config);
    }

    @Test
    void submit_success_zatcaTaxInvoice_transitionsToCleared() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success(
                        "<cleared/>", "<response/>"));

        SubmissionResultDto result = orchestrator.submit(invoiceId);

        assertEquals(SubmissionResult.SUCCESS, result.status());
        verify(stateMachine).transition(any(), eq(InvoiceStatus.SUBMISSION_IN_PROGRESS));
        verify(stateMachine).transition(any(), eq(InvoiceStatus.CLEARED));
    }

    @Test
    void submit_success_zatcaSimplified_transitionsToReported() {
        invoice.setType(InvoiceType.SIMPLIFIED_TAX_INVOICE);
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success(
                        null, "<response/>"));

        orchestrator.submit(invoiceId);

        verify(stateMachine).transition(any(), eq(InvoiceStatus.REPORTED));
    }

    @Test
    void submit_success_eta_transitionsToInReview() {
        invoice.setAuthority(Authority.ETA);
        invoice.setEnvironment(Environment.ETA_PREPRODUCTION);
        config.setAuthority(Authority.ETA);
        config.setEnvironment(Environment.ETA_PREPRODUCTION);

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(authorityConfigRepository.findWithLockByBranchIdAndAuthorityAndEnvironment(
                branch.getId(), Authority.ETA, Environment.ETA_PREPRODUCTION))
                .thenReturn(Optional.of(config));
        when(attemptService.getNextAttemptNumber(invoiceId)).thenReturn(1);
        when(attemptService.createAttempt(any(), eq(1), eq(config)))
                .thenReturn(mockAttempt);
        when(engineFactory.getEngine(Authority.ETA)).thenReturn(engine);
        when(engine.generatePayload(any(), any())).thenReturn("<json/>");
        when(engine.getPayloadArtifactType()).thenReturn(ArtifactType.SIGNED_JSON);
        when(engine.getResponseArtifactType()).thenReturn(ArtifactType.ETA_RESPONSE);
        when(engine.getClearedArtifactType()).thenReturn(ArtifactType.ETA_PDF);

        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.successWithExternalRef(
                        200, "<response/>", "ext-uuid"));

        orchestrator.submit(invoiceId);

        verify(stateMachine).transition(any(), eq(InvoiceStatus.IN_REVIEW));
    }

    @Test
    void submit_rejected_transitionsToRejected() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.rejected(400,
                        List.of("Invalid buyer VAT")));

        SubmissionResultDto result = orchestrator.submit(invoiceId);

        assertEquals(SubmissionResult.REJECTED, result.status());
        verify(stateMachine).transition(any(), eq(InvoiceStatus.REJECTED));
        verify(authorityConfigRepository, never()).save(any());
    }

    @Test
    void submit_exceptionFromEngine_transitionsToFailedRetryable() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.error("Connection refused"));

        SubmissionResultDto result = orchestrator.submit(invoiceId);

        assertEquals(SubmissionResult.ERROR, result.status());
        verify(stateMachine).transition(any(), eq(InvoiceStatus.FAILED_RETRYABLE));
    }

    @Test
    void submit_timeout_transitionsToAmbiguous() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenThrow(new RuntimeException("Read timed out"));

        orchestrator.submit(invoiceId);

        verify(stateMachine).transition(any(),
                eq(InvoiceStatus.SUBMISSION_AMBIGUOUS));
    }

    @Test
    void submit_storesClearedAndResponseArtifacts() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success(
                        "<cleared/>", "<response/>"));

        orchestrator.submit(invoiceId);

        verify(artifactService).storeArtifact(
                any(), eq(ArtifactType.SIGNED_XML), eq("<Invoice/>"));
        verify(artifactService).storeArtifact(
                any(), eq(ArtifactType.ZATCA_RESPONSE), eq("<response/>"));
        verify(artifactService).storeArtifact(
                any(), eq(ArtifactType.CLEARED_XML), eq("<cleared/>"));
    }

    @Test
    void submit_createsAndCompletesAttempt() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success(
                        "<cleared/>", "<response/>"));

        orchestrator.submit(invoiceId);

        verify(attemptService).createAttempt(any(), eq(1), eq(config));
        verify(attemptService).completeAttempt(eq(mockAttempt), any());
    }

    @Test
    void submit_invalidTransition_throwsAndCompletesAttempt() {
        stubCommonMocks();
        when(engine.generatePayload(any(), any()))
                .thenThrow(new InvalidTransitionException(
                        InvoiceStatus.DRAFT, InvoiceStatus.CLEARED));

        assertThrows(InvalidTransitionException.class,
                () -> orchestrator.submit(invoiceId));

        verify(attemptService).completeAttempt(eq(mockAttempt), any());
    }

    @Test
    void submit_unexpectedError_transitionsToFailedNonRetryable() {
        stubCommonMocks();
        when(engine.generatePayload(any(), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        SubmissionResultDto result = orchestrator.submit(invoiceId);

        assertEquals(SubmissionResult.ERROR, result.status());
        verify(stateMachine).transition(any(),
                eq(InvoiceStatus.FAILED_NON_RETRYABLE));
    }

    @Test
    void submit_rejected_doesNotAdvanceHashChain() {
        stubCommonMocks();
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.rejected(400,
                        List.of("Rejected")));

        orchestrator.submit(invoiceId);

        assertEquals(0L, config.getInvoiceCounter());
        assertEquals("seed-hash", config.getPreviousInvoiceHash());
    }

    @Test
    void retry_success_retransmitsInvoice() {
        invoice.setStatus(InvoiceStatus.FAILED_RETRYABLE);
        when(invoiceRepository.findById(invoiceId))
                .thenReturn(Optional.of(invoice));
        when(attemptService.getNextAttemptNumber(invoiceId)).thenReturn(2);
        when(authorityConfigRepository.findWithLockByBranchIdAndAuthorityAndEnvironment(
                branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(Optional.of(config));
        when(attemptService.createAttempt(any(), eq(2), eq(config)))
                .thenReturn(mockAttempt);
        when(engine.generatePayload(any(), any())).thenReturn("<Invoice2/>");
        when(engine.submit(any(), eq("<Invoice2/>"), eq(config)))
                .thenReturn(SubmissionResultDto.success(
                        "<cleared2/>", "<response2/>"));

        SubmissionResultDto result = orchestrator.retry(invoiceId);

        assertEquals(SubmissionResult.SUCCESS, result.status());
        verify(stateMachine).transition(any(),
                eq(InvoiceStatus.SUBMISSION_IN_PROGRESS));
        verify(stateMachine).transition(any(), eq(InvoiceStatus.CLEARED));
    }

    @Test
    void retry_wrongStatus_throws() {
        invoice.setStatus(InvoiceStatus.DRAFT);
        when(invoiceRepository.findById(invoiceId))
                .thenReturn(Optional.of(invoice));

        assertThrows(IllegalStateException.class,
                () -> orchestrator.retry(invoiceId));
    }

    @Test
    void retry_maxRetriesExceeded_transitionsToFailedNonRetryable() {
        invoice.setStatus(InvoiceStatus.FAILED_RETRYABLE);
        when(invoiceRepository.findById(invoiceId))
                .thenReturn(Optional.of(invoice));
        when(attemptService.getNextAttemptNumber(invoiceId))
                .thenReturn(SubmissionOrchestrator.MAX_RETRIES + 1);

        SubmissionResultDto result = orchestrator.retry(invoiceId);

        assertTrue(result.errors().stream()
                .anyMatch(e -> e.contains("Maximum retry attempts")));
        verify(stateMachine).transition(any(),
                eq(InvoiceStatus.FAILED_NON_RETRYABLE));
    }

    @Test
    void retry_logsRetryExhaustedAudit() {
        invoice.setStatus(InvoiceStatus.FAILED_RETRYABLE);
        when(invoiceRepository.findById(invoiceId))
                .thenReturn(Optional.of(invoice));
        when(attemptService.getNextAttemptNumber(invoiceId))
                .thenReturn(SubmissionOrchestrator.MAX_RETRIES + 1);

        orchestrator.retry(invoiceId);

        verify(auditService).log(eq("RETRY_EXHAUSTED"), eq("Invoice"),
                eq(invoiceId.toString()),
                eq(InvoiceStatus.FAILED_RETRYABLE.name()),
                eq(InvoiceStatus.FAILED_NON_RETRYABLE.name()),
                eq(1L));
    }

    @Test
    void computeBackoff_firstRetry_inRange() {
        long backoff = SubmissionOrchestrator.computeBackoff(0);
        assertTrue(backoff >= 2000 - 500);
        assertTrue(backoff <= 2000 + 500);
    }

    @Test
    void computeBackoff_secondRetry_inRange() {
        long backoff = SubmissionOrchestrator.computeBackoff(1);
        assertTrue(backoff >= 4000 - 500);
        assertTrue(backoff <= 4000 + 500);
    }

    @Test
    void computeBackoff_thirdRetry_inRange() {
        long backoff = SubmissionOrchestrator.computeBackoff(2);
        assertTrue(backoff >= 8000 - 500);
        assertTrue(backoff <= 8000 + 500);
    }

    @Test
    void computeBackoff_beyondMax_usesLastBase() {
        long backoff = SubmissionOrchestrator.computeBackoff(5);
        assertTrue(backoff >= 8000 - 500);
        assertTrue(backoff <= 8000 + 500);
    }
}
