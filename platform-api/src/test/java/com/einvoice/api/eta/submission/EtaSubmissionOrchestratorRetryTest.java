package com.einvoice.api.eta.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.eta.submission.service.EtaSubmissionOrchestrator;
import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.repository.eta.EtaInvoiceHeaderRepository;
import com.einvoice.core.repository.eta.EtaReceiptHeaderRepository;
import com.einvoice.core.repository.shared.InvoiceArtifactRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.eta.engine.EtaAuthorityEngine;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class EtaSubmissionOrchestratorRetryTest {

    @Mock private EtaAuthorityEngine engine;
    @Mock private SubmissionAttemptRepository attemptRepository;
    @Mock private InvoiceArtifactRepository artifactRepository;
    @Mock private AuditService auditService;
    @Mock private EtaInvoiceHeaderRepository headerRepository;
    @Mock private EtaReceiptHeaderRepository receiptHeaderRepository;
    @Mock private TransactionTemplate txTemplate;

    private EtaSubmissionOrchestrator orchestrator;

    private UUID headerId;
    private EtaInvoiceHeader header;

    @BeforeEach
    void setUp() {
        orchestrator = new EtaSubmissionOrchestrator(engine, attemptRepository,
                artifactRepository, auditService, headerRepository,
                receiptHeaderRepository, txTemplate);

        headerId = UUID.randomUUID();
        header = EtaInvoiceHeader.builder()
                .id(headerId)
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 2)
                .state(DocumentState.IN_REVIEW)
                .build();

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb =
                    inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> c =
                    inv.getArgument(0);
            c.accept(null);
            return null;
        }).when(txTemplate).executeWithoutResult(any());
        when(headerRepository.findById(headerId)).thenReturn(Optional.of(header));
        when(attemptRepository.findMaxAttemptNumber(headerId))
                .thenReturn(Optional.of(1));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(headerRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SignedPayload signedPayload = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "SHA256");
        EtaAuthorityEngine.Preparation prep = new EtaAuthorityEngine.Preparation(
                signedPayload, "clientId", "clientSecret", "https://token.url");
        when(engine.prepareSubmission(any(), any(), any())).thenReturn(prep);
    }

    @Test
    void retry_success_setsStateValid() {
        AuthorityResponse successResponse = new AuthorityResponse(
                true, "SUCCESS", "eta-uuid-456", "long-id", "sub-id",
                200, null, "{\"uuid\":\"eta-uuid-456\"}");
        when(engine.httpSubmit(any(), any(), any(), any()))
                .thenReturn(successResponse);

        EtaInvoiceHeader result = orchestrator.retry(header);

        assertEquals(DocumentState.ACCEPTED, result.getState());

        verify(attemptRepository).finalizeAttempt(any(),
                any(SubmissionResult.class), any(), any(), any(), any());
    }

    @Test
    void retry_rejected_setsStateRejected() {
        AuthorityResponse rejectedResponse = new AuthorityResponse(
                false, "REJECTED", null, null, null,
                400, "Validation failed", "{\"error\":{}}");
        when(engine.httpSubmit(any(), any(), any(), any()))
                .thenReturn(rejectedResponse);

        EtaInvoiceHeader result = orchestrator.retry(header);

        assertEquals(DocumentState.REJECTED, result.getState());
    }

    @Test
    void retry_attemptNumberIncrements() {
        AuthorityResponse successResponse = new AuthorityResponse(
                true, "SUCCESS", "eta-uuid-456", null, "sub-id",
                200, null, "{}");
        when(engine.httpSubmit(any(), any(), any(), any()))
                .thenReturn(successResponse);

        orchestrator.retry(header);

        verify(attemptRepository).findMaxAttemptNumber(headerId);
    }
}
