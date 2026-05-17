package com.einvoice.api.eta.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.eta.submission.service.EtaSubmissionOrchestrator;
import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.domain.shared.ArtifactType;
import com.einvoice.core.domain.shared.InvoiceArtifact;
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
class EtaSubmissionOrchestratorAmbiguousTest {

    @Mock
    private EtaAuthorityEngine engine;

    @Mock
    private SubmissionAttemptRepository attemptRepository;

    @Mock
    private InvoiceArtifactRepository artifactRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EtaInvoiceHeaderRepository headerRepository;

    @Mock
    private EtaReceiptHeaderRepository receiptHeaderRepository;

    @Mock
    private TransactionTemplate txTemplate;

    private EtaSubmissionOrchestrator orchestrator;

    private UUID headerId;
    private UUID companyId;
    private EtaInvoiceHeader header;

    @BeforeEach
    void setUp() {
        orchestrator = new EtaSubmissionOrchestrator(engine, attemptRepository,
                artifactRepository, auditService, headerRepository,
                receiptHeaderRepository, txTemplate);

        headerId = UUID.randomUUID();
        companyId = UUID.randomUUID();

        header = EtaInvoiceHeader.builder()
                .id(headerId)
                .companyId(companyId)
                .authorityEnvironmentId((short) 1)
                .state(EtaInvoiceState.DRAFT)
                .build();

        doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer =
                    inv.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(headerRepository.findById(headerId)).thenReturn(Optional.of(header));
        when(attemptRepository.findMaxAttemptNumber(headerId)).thenReturn(Optional.empty());
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SignedPayload signedPayload = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "SHA256");
        EtaAuthorityEngine.Preparation prep = new EtaAuthorityEngine.Preparation(
                signedPayload, "clientId", "clientSecret", "https://token.url");
        when(engine.prepareSubmission(any(), any(), any())).thenReturn(prep);
    }

    @Test
    void httpSubmitTimeoutResponse_setsStateAmbiguous() {
        when(engine.httpSubmit(any(), any(), any(), any()))
                .thenReturn(new AuthorityResponse(false, "TIMEOUT", null, null, null,
                        null, "Request timed out", null));

        EtaInvoiceHeader result = orchestrator.submit(header);

        assertEquals(EtaInvoiceState.SUBMISSION_AMBIGUOUS, result.getState());

        ArgumentCaptor<InvoiceArtifact> artifactCaptor = ArgumentCaptor.forClass(InvoiceArtifact.class);
        verify(artifactRepository, times(1)).save(artifactCaptor.capture());
        assertEquals(ArtifactType.SIGNED_JSON, artifactCaptor.getValue().getArtifactType());

        verify(artifactRepository, never()).save(argThat(a ->
                a != null && a.getArtifactType() == ArtifactType.ETA_RESPONSE));

        verify(attemptRepository).finalizeAttempt(any(), any(), any(), any(), any(), any());
    }

    @Test
    void httpSubmitException_setsStateAmbiguous() {
        when(engine.httpSubmit(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        EtaInvoiceHeader result = orchestrator.submit(header);

        assertEquals(EtaInvoiceState.SUBMISSION_AMBIGUOUS, result.getState());

        ArgumentCaptor<InvoiceArtifact> artifactCaptor = ArgumentCaptor.forClass(InvoiceArtifact.class);
        verify(artifactRepository, times(1)).save(artifactCaptor.capture());
        assertEquals(ArtifactType.SIGNED_JSON, artifactCaptor.getValue().getArtifactType());

        verify(artifactRepository, never()).save(argThat(a ->
                a != null && a.getArtifactType() == ArtifactType.ETA_RESPONSE));

        ArgumentCaptor<SubmissionResult> resultCaptor = ArgumentCaptor.forClass(SubmissionResult.class);
        verify(attemptRepository).finalizeAttempt(any(), resultCaptor.capture(), any(), any(), any(), any());
        assertEquals(SubmissionResult.AMBIGUOUS, resultCaptor.getValue());
    }
}
