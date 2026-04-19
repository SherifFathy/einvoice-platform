package com.einvoice.eta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceArtifact;
import com.einvoice.core.domain.enums.ArtifactType;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.InvoiceRepository;
import com.einvoice.core.service.AuditService;
import com.einvoice.core.service.AuthorityEngineFactory;
import com.einvoice.core.service.InvoiceArtifactService;
import com.einvoice.core.service.InvoiceStateMachine;
import com.einvoice.core.service.SubmissionAttemptService;
import com.einvoice.core.service.SubmissionOrchestrator;
import com.einvoice.core.service.SubmissionResultDto;
import com.einvoice.eta.client.EtaSubmissionClient;
import com.einvoice.eta.serializer.EtaInvoiceSerializer;
import com.einvoice.eta.signing.EtaSigningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EtaEndToEndSubmissionTest {

    private InvoiceRepository invoiceRepository;
    private AuthorityConfigRepository authorityConfigRepository;
    private InvoiceStateMachine stateMachine;
    private InvoiceArtifactService artifactService;
    private SubmissionAttemptService attemptService;
    private AuditService auditService;
    private SubmissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        authorityConfigRepository = mock(AuthorityConfigRepository.class);
        stateMachine = mock(InvoiceStateMachine.class);
        attemptService = mock(SubmissionAttemptService.class);
        auditService = mock(AuditService.class);

        ObjectMapper objectMapper = new ObjectMapper();

        EtaSubmissionClient submissionClient = mock(EtaSubmissionClient.class);

        String etaSuccessResponse = "{\"status\":200,"
                + "\"acceptedDocuments\":[{\"uuid\":\"eta-doc-uuid-123\","
                + "\"longId\":\"long-id-456\",\"internalId\":\"INV-001\"}]}";

        when(submissionClient.submit(anyString(), anyString(),
                any(AuthorityConfig.class)))
                .thenReturn(SubmissionResultDto.successWithExternalRef(
                        200, etaSuccessResponse, "eta-doc-uuid-123"));

        EtaInvoiceSerializer serializer =
                new EtaInvoiceSerializer(objectMapper);

        EtaSigningService signingService = mock(EtaSigningService.class);
        org.bouncycastle.cms.CMSSignedData cmsData = mock(
                org.bouncycastle.cms.CMSSignedData.class);
        when(signingService.sign(anyString(), any(AuthorityConfig.class)))
                .thenReturn(new EtaSigningService.EtaSigningResult(
                        "base64signature", cmsData));

        EtaAuthorityEngine engine = new EtaAuthorityEngine(
                serializer, signingService, submissionClient);

        AuthorityEngineFactory engineFactory = mock(AuthorityEngineFactory.class);
        when(engineFactory.getEngine(Authority.ETA)).thenReturn(engine);

        artifactService = mock(InvoiceArtifactService.class);
        when(artifactService.storeArtifact(any(), any(),
                anyString())).thenAnswer(invocation -> {
                    Invoice invoiceArg = invocation.getArgument(0);
                    ArtifactType type = invocation.getArgument(1);
                    String content = invocation.getArgument(2);
                    return InvoiceArtifact.builder()
                            .invoice(invoiceArg)
                            .artifactType(type)
                            .content(content)
                            .contentHash("sha256hash")
                            .build();
                });

        when(attemptService.getNextAttemptNumber(any()))
                .thenReturn(1);
        when(attemptService.createAttempt(any(), eq(1),
                any(AuthorityConfig.class)))
                .thenReturn(com.einvoice.core.domain.SubmissionAttempt.builder()
                        .id(1L)
                        .attemptNumber(1)
                        .authority(Authority.ETA)
                        .environment(Environment.ETA_PREPRODUCTION)
                        .result(com.einvoice.core.domain.enums.SubmissionResult.SUCCESS)
                        .build());

        orchestrator = new SubmissionOrchestrator(
                invoiceRepository, authorityConfigRepository,
                stateMachine, engineFactory, artifactService,
                attemptService, auditService);
    }

    @Test
    void etaSubmission_success_setsInvoiceInReview() {
        Invoice invoice = buildTestInvoice();
        AuthorityConfig config = buildTestConfig();

        when(invoiceRepository.findById(invoice.getId()))
                .thenReturn(Optional.of(invoice));
        when(authorityConfigRepository
                .findWithLockByBranchIdAndAuthorityAndEnvironment(
                        any(), eq(Authority.ETA),
                        eq(Environment.ETA_PREPRODUCTION)))
                .thenReturn(Optional.of(config));

        SubmissionResultDto result = orchestrator.submit(invoice.getId());

        assertEquals(com.einvoice.core.domain.enums.SubmissionResult.SUCCESS,
                result.status());
        assertEquals("eta-doc-uuid-123", result.externalReference());
        assertNull(result.clearedDocument(),
                "clearedDocument must be null for ETA (no null artifact type)");

        ArgumentCaptor<InvoiceStatus> statusCaptor =
                ArgumentCaptor.forClass(InvoiceStatus.class);
        verify(stateMachine, times(2)).transition(any(Invoice.class),
                statusCaptor.capture());
        assertEquals(InvoiceStatus.SUBMISSION_IN_PROGRESS,
                statusCaptor.getAllValues().get(0));
        assertEquals(InvoiceStatus.IN_REVIEW,
                statusCaptor.getAllValues().get(1));
    }

    @Test
    void etaSubmission_success_persistsCorrectArtifacts() {
        Invoice invoice = buildTestInvoice();
        AuthorityConfig config = buildTestConfig();

        when(invoiceRepository.findById(invoice.getId()))
                .thenReturn(Optional.of(invoice));
        when(authorityConfigRepository
                .findWithLockByBranchIdAndAuthorityAndEnvironment(
                        any(), eq(Authority.ETA),
                        eq(Environment.ETA_PREPRODUCTION)))
                .thenReturn(Optional.of(config));

        orchestrator.submit(invoice.getId());

        ArgumentCaptor<ArtifactType> typeCaptor =
                ArgumentCaptor.forClass(ArtifactType.class);
        verify(artifactService, times(3)).storeArtifact(any(),
                typeCaptor.capture(), anyString());

        List<ArtifactType> callTypes =
                typeCaptor.getAllValues().stream().toList();

        org.junit.jupiter.api.Assertions.assertTrue(
                callTypes.contains(ArtifactType.SIGNED_JSON),
                "SIGNED_JSON artifact must be stored");
        org.junit.jupiter.api.Assertions.assertTrue(
                callTypes.contains(ArtifactType.ETA_CADES_SIG),
                "ETA_CADES_SIG artifact must be stored");
        org.junit.jupiter.api.Assertions.assertTrue(
                callTypes.contains(ArtifactType.ETA_RESPONSE),
                "ETA_RESPONSE artifact must be stored");
    }

    @Test
    void etaSubmission_success_setsExternalInvoiceReference() {
        Invoice invoice = buildTestInvoice();
        AuthorityConfig config = buildTestConfig();

        when(invoiceRepository.findById(invoice.getId()))
                .thenReturn(Optional.of(invoice));
        when(authorityConfigRepository
                .findWithLockByBranchIdAndAuthorityAndEnvironment(
                        any(), eq(Authority.ETA),
                        eq(Environment.ETA_PREPRODUCTION)))
                .thenReturn(Optional.of(config));

        orchestrator.submit(invoice.getId());

        assertEquals("eta-doc-uuid-123",
                invoice.getExternalInvoiceReference(),
                "externalInvoiceReference must be set from ETA response uuid");
    }

    private Invoice buildTestInvoice() {
        Company company = Company.builder()
                .id(1L)
                .nameEn("Test Co")
                .vatNumber("300000000000001")
                .build();
        Branch branch = Branch.builder().id(1L).company(company).build();
        Customer buyer = Customer.builder()
                .id(1L).nameEn("Buyer").vatNumber("300000000000002").build();

        Invoice invoice = Invoice.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .company(company)
                .branch(branch)
                .buyer(buyer)
                .invoiceNumber("INV-001")
                .type(InvoiceType.TAX_INVOICE)
                .authority(Authority.ETA)
                .environment(Environment.ETA_PREPRODUCTION)
                .status(InvoiceStatus.READY_FOR_SUBMISSION)
                .issueDate(LocalDate.of(2026, 4, 17))
                .totalLineNet(new BigDecimal("1000.00"))
                .totalWithoutVat(new BigDecimal("1000.00"))
                .totalVat(new BigDecimal("140.00"))
                .totalWithVat(new BigDecimal("1140.00"))
                .amountDue(new BigDecimal("1140.00"))
                .totalAllowances(BigDecimal.ZERO)
                .prepaidAmount(BigDecimal.ZERO)
                .createdAt(OffsetDateTime.parse("2026-04-17T00:00:00Z"))
                .build();
        return invoice;
    }

    private AuthorityConfig buildTestConfig() {
        return AuthorityConfig.builder()
                .id(1L)
                .branch(Branch.builder().id(1L)
                        .company(Company.builder().id(1L).build()).build())
                .authority(Authority.ETA)
                .environment(Environment.ETA_PREPRODUCTION)
                .csidEncrypted("client-id".getBytes(StandardCharsets.UTF_8))
                .credentialsEncrypted(
                        "client-secret".getBytes(StandardCharsets.UTF_8))
                .certificateEncrypted("cert-bytes".getBytes(StandardCharsets.UTF_8))
                .privateKeyEncrypted("key-bytes".getBytes(StandardCharsets.UTF_8))
                .build();
    }
}
