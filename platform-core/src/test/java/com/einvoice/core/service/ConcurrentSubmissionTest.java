package com.einvoice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Wiring-level tests for submission hash-chain updates.
 *
 * <p><b>Scope note:</b> These tests use Mockito stubs for repositories and the
 * authority engine. They verify that {@link SubmissionOrchestrator} invokes the
 * pessimistic-lock query and that a second sequential submission observes the
 * first submission's stored hash. They do <b>not</b> exercise real database
 * locking, because the mocked repository cannot enforce
 * {@code LockModeType.PESSIMISTIC_WRITE} semantics.
 *
 * <p>A true concurrent-serialization test (two transactions on separate threads
 * against a real PostgreSQL instance via Testcontainers) is deferred to Phase 13
 * polish; see tasks.md T102a follow-up.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConcurrentSubmissionTest {

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

    private Company company;
    private Branch branch;
    private AuthorityConfig config;

    @BeforeEach
    void setUp() {
        orchestrator = new SubmissionOrchestrator(
                invoiceRepository, authorityConfigRepository,
                stateMachine, engineFactory, artifactService,
                attemptService, auditService);

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
        when(authorityConfigRepository.findWithLockByBranchIdAndAuthorityAndEnvironment(
                branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(Optional.of(config));
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

    @Test
    void submitInvokesLockingRepositoryQuery() {
        UUID id = UUID.randomUUID();
        Invoice inv = createInvoice(id);

        when(invoiceRepository.findById(id)).thenReturn(Optional.of(inv));
        when(attemptService.getNextAttemptNumber(id)).thenReturn(1);
        when(engine.generatePayload(any(), any())).thenReturn("<Invoice/>");
        when(engine.computeInvoiceHash("<Invoice/>")).thenReturn("hash123");
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));

        orchestrator.submit(id);

        verify(authorityConfigRepository)
                .findWithLockByBranchIdAndAuthorityAndEnvironment(
                        eq(branch.getId()),
                        eq(Authority.ZATCA),
                        eq(Environment.ZATCA_SANDBOX));
    }

    @Test
    void secondSubmissionObservesFirstSubmissionsStoredHash() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Invoice inv1 = createInvoice(id1);
        Invoice inv2 = createInvoice(id2);

        when(invoiceRepository.findById(id1)).thenReturn(Optional.of(inv1));
        when(invoiceRepository.findById(id2)).thenReturn(Optional.of(inv2));
        when(attemptService.getNextAttemptNumber(id1)).thenReturn(1);
        when(attemptService.getNextAttemptNumber(id2)).thenReturn(1);
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));

        when(engine.generatePayload(any(), any())).thenReturn("<Invoice>1</Invoice>");
        when(engine.computeInvoiceHash("<Invoice>1</Invoice>")).thenReturn("hash-1");
        orchestrator.submit(id1);

        assertEquals("hash-1", config.getPreviousInvoiceHash(),
                "First submission must store its hash on the config");
        assertEquals(1L, config.getInvoiceCounter());

        AtomicReference<String> observedPreviousHash = new AtomicReference<>();
        when(engine.generatePayload(any(), any())).thenAnswer(invocation -> {
            AuthorityConfig cfg = invocation.getArgument(1);
            observedPreviousHash.set(cfg.getPreviousInvoiceHash());
            return "<Invoice>2</Invoice>";
        });
        when(engine.computeInvoiceHash("<Invoice>2</Invoice>")).thenReturn("hash-2");
        orchestrator.submit(id2);

        assertEquals("hash-1", observedPreviousHash.get(),
                "Second submission must observe the first submission's hash");
        assertEquals("hash-2", config.getPreviousInvoiceHash());
        assertEquals(2L, config.getInvoiceCounter());
    }

    @Test
    void twoSequentialSubmissions_counterIncrementsMonotonically() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Invoice inv1 = createInvoice(id1);
        Invoice inv2 = createInvoice(id2);

        when(invoiceRepository.findById(id1)).thenReturn(Optional.of(inv1));
        when(invoiceRepository.findById(id2)).thenReturn(Optional.of(inv2));
        when(attemptService.getNextAttemptNumber(id1)).thenReturn(1);
        when(attemptService.getNextAttemptNumber(id2)).thenReturn(1);
        when(engine.submit(any(), anyString(), any()))
                .thenReturn(SubmissionResultDto.success("<cleared/>", "<response/>"));

        when(engine.generatePayload(any(), any())).thenReturn("<Invoice>a</Invoice>");
        when(engine.computeInvoiceHash("<Invoice>a</Invoice>")).thenReturn("h-a");
        orchestrator.submit(id1);

        when(engine.generatePayload(any(), any())).thenReturn("<Invoice>b</Invoice>");
        when(engine.computeInvoiceHash("<Invoice>b</Invoice>")).thenReturn("h-b");
        orchestrator.submit(id2);

        assertEquals(2L, config.getInvoiceCounter());
        assertNotNull(config.getPreviousInvoiceHash());
        assertEquals("h-b", config.getPreviousInvoiceHash());
    }
}
