package com.einvoice.api.eta.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.einvoice.api.eta.submission.service.BulkStatusCheckExecutor;
import com.einvoice.api.eta.submission.service.BulkStatusCheckExecutor.BulkStatusOutcome;
import com.einvoice.api.eta.submission.service.EtaSubmissionOrchestrator;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.error.BulkBatchLimitExceededException;
import com.einvoice.core.repository.eta.EtaInvoiceHeaderRepository;
import com.einvoice.core.repository.eta.EtaReceiptHeaderRepository;
import com.einvoice.security.permission.PermissionService;
import com.einvoice.security.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkStatusCheckExecutorTest {

    @Mock
    private EtaSubmissionOrchestrator orchestrator;

    @Mock
    private EtaInvoiceHeaderRepository invoiceHeaderRepository;

    @Mock
    private EtaReceiptHeaderRepository receiptHeaderRepository;

    @Mock
    private PermissionService permissionService;

    private ExecutorService pool;
    private BulkStatusCheckExecutor executor;
    private UUID userId;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(8);
        executor = new BulkStatusCheckExecutor(orchestrator,
                invoiceHeaderRepository, receiptHeaderRepository,
                permissionService, pool);
        userId = UUID.randomUUID();
        TenantContext.set(new TenantContext.Holder(
                userId, UUID.randomUUID(), (short) 2,
                "ETA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
                false, System.currentTimeMillis(), "jti"));
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        TenantContext.clear();
    }

    @Test
    void batchSize201_throwsException() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            ids.add(UUID.randomUUID());
        }

        assertThrows(BulkBatchLimitExceededException.class,
                () -> executor.runBulk(TransactionType.INVOICE, ids));
    }

    @Test
    void parallelExecution_returnsPerIdOutcomes() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ids.add(UUID.randomUUID());
        }

        when(permissionService.hasPermission(any(), any(), anyShort(),
                any(), any()))
                .thenReturn(true);

        for (UUID id : ids) {
            EtaInvoiceHeader header = EtaInvoiceHeader.builder()
                    .id(id)
                    .companyId(UUID.randomUUID())
                    .authorityEnvironmentId((short) 2)
                    .state(EtaInvoiceState.IN_REVIEW)
                    .build();
            when(invoiceHeaderRepository.findById(id))
                    .thenReturn(Optional.of(header));
            when(orchestrator.checkStatus(any(EtaInvoiceHeader.class)))
                    .thenReturn(header);
        }

        List<BulkStatusOutcome> outcomes =
                executor.runBulk(TransactionType.INVOICE, ids);

        assertEquals(50, outcomes.size());
    }

    @Test
    void unknownId_returnsNotFoundOutcome() {
        UUID unknownId = UUID.randomUUID();
        when(invoiceHeaderRepository.findById(unknownId))
                .thenReturn(Optional.empty());

        List<BulkStatusOutcome> outcomes =
                executor.runBulk(TransactionType.INVOICE, List.of(unknownId));

        assertEquals(1, outcomes.size());
        assertEquals("NOT_FOUND", outcomes.get(0).outcome());
        assertEquals(unknownId, outcomes.get(0).documentId());
    }

    @Test
    void forbiddenPermission_returnsForbiddenOutcome() {
        UUID docId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        EtaInvoiceHeader header = EtaInvoiceHeader.builder()
                .id(docId)
                .companyId(companyId)
                .authorityEnvironmentId((short) 2)
                .state(EtaInvoiceState.IN_REVIEW)
                .build();
        when(invoiceHeaderRepository.findById(docId))
                .thenReturn(Optional.of(header));
        when(permissionService.hasPermission(any(), any(), anyShort(),
                any(), any()))
                .thenReturn(false);

        List<BulkStatusOutcome> outcomes =
                executor.runBulk(TransactionType.INVOICE, List.of(docId));

        assertEquals(1, outcomes.size());
        assertEquals("FORBIDDEN", outcomes.get(0).outcome());
        assertEquals(docId, outcomes.get(0).documentId());
    }

    @Test
    void mixedPermissionOutcomes_forbiddenCoexistsWithUpdated() {
        UUID allowedId = UUID.randomUUID();
        UUID forbiddenId = UUID.randomUUID();
        UUID companyId1 = UUID.randomUUID();
        UUID companyId2 = UUID.randomUUID();

        EtaInvoiceHeader allowedHeader = EtaInvoiceHeader.builder()
                .id(allowedId)
                .companyId(companyId1)
                .authorityEnvironmentId((short) 2)
                .state(EtaInvoiceState.IN_REVIEW)
                .build();
        EtaInvoiceHeader forbiddenHeader = EtaInvoiceHeader.builder()
                .id(forbiddenId)
                .companyId(companyId2)
                .authorityEnvironmentId((short) 2)
                .state(EtaInvoiceState.IN_REVIEW)
                .build();

        when(invoiceHeaderRepository.findById(allowedId))
                .thenReturn(Optional.of(allowedHeader));
        when(invoiceHeaderRepository.findById(forbiddenId))
                .thenReturn(Optional.of(forbiddenHeader));

        when(permissionService.hasPermission(any(), eq(companyId1), anyShort(),
                eq("INVOICE"), eq("REFRESH")))
                .thenReturn(true);
        when(permissionService.hasPermission(any(), eq(companyId2), anyShort(),
                eq("INVOICE"), eq("REFRESH")))
                .thenReturn(false);

        EtaInvoiceHeader updatedHeader = EtaInvoiceHeader.builder()
                .id(allowedId)
                .companyId(companyId1)
                .authorityEnvironmentId((short) 2)
                .state(EtaInvoiceState.VALID)
                .build();
        when(orchestrator.checkStatus(any(EtaInvoiceHeader.class)))
                .thenReturn(updatedHeader);

        List<BulkStatusOutcome> outcomes =
                executor.runBulk(TransactionType.INVOICE,
                        List.of(allowedId, forbiddenId));

        assertEquals(2, outcomes.size());
        boolean hasForbidden = outcomes.stream()
                .anyMatch(o -> "FORBIDDEN".equals(o.outcome()));
        boolean hasUpdated = outcomes.stream()
                .anyMatch(o -> "UPDATED".equals(o.outcome()));
        assertTrue(hasForbidden, "Expected FORBIDDEN outcome");
        assertTrue(hasUpdated, "Expected UPDATED outcome");
    }
}
