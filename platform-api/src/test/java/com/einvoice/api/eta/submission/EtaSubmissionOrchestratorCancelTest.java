package com.einvoice.api.eta.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.eta.submission.service.EtaSubmissionOrchestrator;
import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class EtaSubmissionOrchestratorCancelTest {

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
                .state(EtaInvoiceState.VALID)
                .etaUuid("eta-uuid-123")
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
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(headerRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cancel_success_setsStateCancelled() {
        AuthorityResponse successResponse = new AuthorityResponse(
                true, "SUCCESS", "eta-uuid-123", null, null,
                200, null, "{}");
        when(engine.cancel(any())).thenReturn(successResponse);

        EtaInvoiceHeader result = orchestrator.cancel(header, "wrong invoice");

        assertEquals(EtaInvoiceState.CANCELLED, result.getState());

        verify(attemptRepository).finalizeAttempt(any(),
                any(SubmissionResult.class), any(), any(), any(), any());

        verify(engine, times(1)).cancel(any());
    }

    @Test
    void cancel_rejectedByEta_staysValid() {
        AuthorityResponse rejectedResponse = new AuthorityResponse(
                false, "REJECTED", null, null, null,
                400, "Cancellation window expired", "{}");
        when(engine.cancel(any())).thenReturn(rejectedResponse);

        EtaInvoiceHeader result = orchestrator.cancel(header, "wrong invoice");

        assertEquals(EtaInvoiceState.VALID, result.getState());

        verify(attemptRepository).finalizeAttempt(any(),
                any(SubmissionResult.class), any(), any(), any(), any());
    }

    @Test
    void cancel_singleOutboundCallRegardlessOfTime() {
        AuthorityResponse response = new AuthorityResponse(
                true, "SUCCESS", "eta-uuid-123", null, null,
                200, null, "{}");
        when(engine.cancel(any())).thenReturn(response);

        orchestrator.cancel(header, "reason");

        verify(engine, times(1)).cancel(any());
    }
}
