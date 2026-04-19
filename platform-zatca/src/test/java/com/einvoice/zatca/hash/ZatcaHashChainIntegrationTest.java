package com.einvoice.zatca.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.InvoiceRepository;
import com.einvoice.core.service.AuditService;
import com.einvoice.core.service.AuthorityEngine;
import com.einvoice.core.service.AuthorityEngineFactory;
import com.einvoice.core.service.InvoiceArtifactService;
import com.einvoice.core.service.InvoiceStateMachine;
import com.einvoice.core.service.SubmissionAttemptService;
import com.einvoice.core.service.SubmissionOrchestrator;
import com.einvoice.core.service.SubmissionResultDto;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZatcaHashChainIntegrationTest {

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
    private ZatcaHashService hashService;

    private Company company;
    private Branch branch;
    private AuthorityConfig config;

    @BeforeEach
    void setUp() {
        orchestrator = new SubmissionOrchestrator(
                invoiceRepository, authorityConfigRepository,
                stateMachine, engineFactory, artifactService,
                attemptService, auditService);
        hashService = new ZatcaHashService();

        company = Company.builder().build();
        company.setId(1L);
        company.setNameEn("Test Company");
        company.setVatNumber("300000000000003");

        branch = Branch.builder().build();
        branch.setId(100L);
        branch.setCompany(company);

        config = AuthorityConfig.builder()
                .branch(branch)
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .invoiceCounter(0L)
                .previousInvoiceHash(null)
                .build();

        when(engineFactory.getEngine(Authority.ZATCA)).thenReturn(engine);
        when(engine.getPayloadArtifactType()).thenReturn(ArtifactType.SIGNED_XML);
        when(engine.getResponseArtifactType()).thenReturn(ArtifactType.ZATCA_RESPONSE);
        when(engine.getClearedArtifactType()).thenReturn(ArtifactType.CLEARED_XML);
        when(attemptService.createAttempt(any(), any(Integer.class), any()))
                .thenReturn(mockAttempt);
    }

    private Invoice createInvoice(UUID id) {
        return Invoice.builder()
                .id(id)
                .company(company)
                .branch(branch)
                .authority(Authority.ZATCA)
                .type(InvoiceType.TAX_INVOICE)
                .environment(Environment.ZATCA_SANDBOX)
                .status(InvoiceStatus.READY_FOR_SUBMISSION)
                .issueDate(LocalDate.now())
                .build();
    }

    private void stubSubmit(Invoice invoice, String payloadXml, String computedHash) {
        when(invoiceRepository.findById(invoice.getId()))
                .thenReturn(Optional.of(invoice));
        when(authorityConfigRepository.findWithLockByBranchIdAndAuthorityAndEnvironment(
                branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(Optional.of(config));
        when(attemptService.getNextAttemptNumber(invoice.getId())).thenReturn(1);
        when(engine.generatePayload(any(), any())).thenReturn(payloadXml);
        when(engine.computeInvoiceHash(payloadXml)).thenReturn(computedHash);
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));
    }

    @Nested
    class FirstInvoiceSeedHash {

        @Test
        void firstSuccessfulSubmissionStoresComputedHashOnConfig() {
            Invoice invoice = createInvoice(UUID.randomUUID());
            String payload = "<Invoice>first</Invoice>";
            String hash = "hash-invoice-1";

            stubSubmit(invoice, payload, hash);

            orchestrator.submit(invoice.getId());

            assertNotNull(config.getPreviousInvoiceHash());
            assertEquals(hash, config.getPreviousInvoiceHash());
            assertEquals(1L, config.getInvoiceCounter());
        }

        @Test
        void firstInvoiceSeedHashIsUsedByHashService() {
            String seedHash = hashService.getPreviousHashBase64(null);
            assertNotNull(seedHash);
            assertNotEquals("", seedHash);
            assertEquals(hashService.getSeedHash(), seedHash);
        }
    }

    @Nested
    class SequentialChain {

        @Test
        void threeSequentialInvoicesChainCorrectly() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            Invoice inv1 = createInvoice(id1);
            final Invoice inv2 = createInvoice(id2);
            final Invoice inv3 = createInvoice(id3);

            String payload1 = "<Invoice>first</Invoice>";
            String payload2 = "<Invoice>second</Invoice>";
            String payload3 = "<Invoice>third</Invoice>";
            String hash1 = hashService.computeHash(payload1);
            final String hash2 = hashService.computeHash(payload2);
            final String hash3 = hashService.computeHash(payload3);

            stubSubmit(inv1, payload1, hash1);
            orchestrator.submit(id1);
            assertEquals(hash1, config.getPreviousInvoiceHash());
            assertEquals(1L, config.getInvoiceCounter());

            when(invoiceRepository.findById(id2)).thenReturn(Optional.of(inv2));
            when(attemptService.getNextAttemptNumber(id2)).thenReturn(1);
            when(engine.generatePayload(any(), any())).thenReturn(payload2);
            when(engine.computeInvoiceHash(payload2)).thenReturn(hash2);
            orchestrator.submit(id2);
            assertEquals(hash2, config.getPreviousInvoiceHash());
            assertEquals(2L, config.getInvoiceCounter());

            when(invoiceRepository.findById(id3)).thenReturn(Optional.of(inv3));
            when(attemptService.getNextAttemptNumber(id3)).thenReturn(1);
            when(engine.generatePayload(any(), any())).thenReturn(payload3);
            when(engine.computeInvoiceHash(payload3)).thenReturn(hash3);
            orchestrator.submit(id3);
            assertEquals(hash3, config.getPreviousInvoiceHash());
            assertEquals(3L, config.getInvoiceCounter());
        }

        @Test
        void counterIncrementsOnEachSuccessfulSubmission() {
            for (int i = 0; i < 5; i++) {
                UUID id = UUID.randomUUID();
                Invoice inv = createInvoice(id);
                String payload = "<Invoice>inv-" + i + "</Invoice>";
                String hash = "hash-" + i;

                when(invoiceRepository.findById(id)).thenReturn(Optional.of(inv));
                when(authorityConfigRepository.findWithLockByBranchIdAndAuthorityAndEnvironment(
                        branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX))
                        .thenReturn(Optional.of(config));
                when(attemptService.getNextAttemptNumber(id)).thenReturn(1);
                when(engine.generatePayload(any(), any())).thenReturn(payload);
                when(engine.computeInvoiceHash(payload)).thenReturn(hash);
                when(engine.submit(any(), anyString(), any()))
                        .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));

                orchestrator.submit(id);
                assertEquals(i + 1L, config.getInvoiceCounter());
                assertEquals(hash, config.getPreviousInvoiceHash());
            }
        }
    }

    @Nested
    class FailureDoesNotAdvanceChain {

        @Test
        void rejectedSubmission_doesNotAdvanceHashChain() {
            Invoice invoice = createInvoice(UUID.randomUUID());
            String payload = "<Invoice>rejected</Invoice>";
            String hash = "hash-rejected";

            stubSubmit(invoice, payload, hash);
            when(engine.submit(any(), anyString(), any()))
                    .thenReturn(SubmissionResultDto.rejected(400,
                            java.util.List.of("Invalid buyer VAT")));

            orchestrator.submit(invoice.getId());

            assertEquals(0L, config.getInvoiceCounter());
            assertEquals(null, config.getPreviousInvoiceHash());
            verify(authorityConfigRepository, never()).save(any());
        }

        @Test
        void errorSubmission_doesNotAdvanceHashChain() {
            Invoice invoice = createInvoice(UUID.randomUUID());
            String payload = "<Invoice>error</Invoice>";
            String hash = "hash-error";

            stubSubmit(invoice, payload, hash);
            when(engine.submit(any(), anyString(), any()))
                    .thenReturn(SubmissionResultDto.error("Connection refused"));

            orchestrator.submit(invoice.getId());

            assertEquals(0L, config.getInvoiceCounter());
            assertEquals(null, config.getPreviousInvoiceHash());
            verify(authorityConfigRepository, never()).save(any());
        }

        @Test
        void timeoutSubmission_doesNotAdvanceHashChain() {
            Invoice invoice = createInvoice(UUID.randomUUID());
            String payload = "<Invoice>timeout</Invoice>";

            stubSubmit(invoice, payload, "hash-timeout");
            when(engine.submit(any(), anyString(), any()))
                    .thenThrow(new RuntimeException("Read timed out"));

            orchestrator.submit(invoice.getId());

            assertEquals(0L, config.getInvoiceCounter());
            assertEquals(null, config.getPreviousInvoiceHash());
            verify(authorityConfigRepository, never()).save(any());
        }

        @Test
        void ambiguousSubmission_doesNotAdvanceHashChain() {
            Invoice invoice = createInvoice(UUID.randomUUID());
            String payload = "<Invoice>ambiguous</Invoice>";

            stubSubmit(invoice, payload, "hash-ambiguous");
            when(engine.submit(any(), anyString(), any()))
                    .thenReturn(SubmissionResultDto.ambiguous());

            orchestrator.submit(invoice.getId());

            assertEquals(0L, config.getInvoiceCounter());
            assertEquals(null, config.getPreviousInvoiceHash());
            verify(authorityConfigRepository, never()).save(any());
        }

        @Test
        void successfulAfterFailedSubmission_advancesChain() {
            Invoice failed = createInvoice(UUID.randomUUID());
            String failedPayload = "<Invoice>failed</Invoice>";
            String failedHash = "hash-failed";

            stubSubmit(failed, failedPayload, failedHash);
            when(engine.submit(any(), anyString(), any()))
                    .thenReturn(SubmissionResultDto.rejected(400,
                            java.util.List.of("Rejected")));
            orchestrator.submit(failed.getId());
            assertEquals(0L, config.getInvoiceCounter());

            Invoice success = createInvoice(UUID.randomUUID());
            String successPayload = "<Invoice>success</Invoice>";
            String successHash = "hash-success";

            when(invoiceRepository.findById(success.getId()))
                    .thenReturn(Optional.of(success));
            when(attemptService.getNextAttemptNumber(success.getId())).thenReturn(1);
            when(engine.generatePayload(any(), any())).thenReturn(successPayload);
            when(engine.computeInvoiceHash(successPayload)).thenReturn(successHash);
            when(engine.submit(any(), anyString(), any()))
                    .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));

            orchestrator.submit(success.getId());
            assertEquals(1L, config.getInvoiceCounter());
            assertEquals(successHash, config.getPreviousInvoiceHash());
        }
    }
}
