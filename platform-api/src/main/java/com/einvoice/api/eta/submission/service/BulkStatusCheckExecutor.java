package com.einvoice.api.eta.submission.service;

import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.domain.eta.lifecycle.EtaReceiptState;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.error.BulkBatchLimitExceededException;
import com.einvoice.core.repository.eta.EtaInvoiceHeaderRepository;
import com.einvoice.core.repository.eta.EtaReceiptHeaderRepository;
import com.einvoice.security.permission.PermissionService;
import com.einvoice.security.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Executes bulk ETA document status checks in parallel using a bounded thread pool.
 */
@Service
public class BulkStatusCheckExecutor {

    private static final int MAX_BATCH_SIZE = 200;

    private final EtaSubmissionOrchestrator orchestrator;
    private final EtaInvoiceHeaderRepository invoiceHeaderRepository;
    private final EtaReceiptHeaderRepository receiptHeaderRepository;
    private final PermissionService permissionService;
    private final ExecutorService pool;

    /**
     * Constructs a BulkStatusCheckExecutor.
     *
     * @param orchestrator the ETA submission orchestrator
     * @param invoiceHeaderRepository the invoice header repository
     * @param receiptHeaderRepository the receipt header repository
     * @param permissionService the permission service
     * @param etaBulkStatusPool the thread pool for parallel status checks
     */
    public BulkStatusCheckExecutor(EtaSubmissionOrchestrator orchestrator,
            EtaInvoiceHeaderRepository invoiceHeaderRepository,
            EtaReceiptHeaderRepository receiptHeaderRepository,
            PermissionService permissionService,
            ExecutorService etaBulkStatusPool) {
        this.orchestrator = orchestrator;
        this.invoiceHeaderRepository = invoiceHeaderRepository;
        this.receiptHeaderRepository = receiptHeaderRepository;
        this.permissionService = permissionService;
        this.pool = etaBulkStatusPool;
    }

    /**
     * Runs a bulk status check for the given documents.
     *
     * @param transactionType the type of documents to check
     * @param documentIds the IDs of the documents to check
     * @return the outcomes of each status check
     */
    public List<BulkStatusOutcome> runBulk(TransactionType transactionType,
            List<UUID> documentIds) {
        if (documentIds.size() > MAX_BATCH_SIZE) {
            throw new BulkBatchLimitExceededException(
                    "Batch size " + documentIds.size()
                            + " exceeds maximum of " + MAX_BATCH_SIZE,
                    documentIds.size(), MAX_BATCH_SIZE);
        }

        UUID capturedUserId = TenantContext.getUserId();
        TenantContext.Holder capturedContext = TenantContext.current();
        RequestAttributes capturedRequestAttrs =
                RequestContextHolder.getRequestAttributes();

        List<BulkStatusOutcome> outcomes =
                new ArrayList<>(documentIds.size());
        java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(documentIds.size());

        for (UUID docId : documentIds) {
            pool.submit(() -> {
                try {
                    if (capturedContext != null) {
                        TenantContext.set(capturedContext);
                    }
                    if (capturedRequestAttrs != null) {
                        RequestContextHolder.setRequestAttributes(
                                capturedRequestAttrs);
                    }
                    BulkStatusOutcome outcome =
                            checkSingle(transactionType, docId,
                                    capturedUserId);
                    synchronized (outcomes) {
                        outcomes.add(outcome);
                    }
                } finally {
                    TenantContext.clear();
                    RequestContextHolder.resetRequestAttributes();
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return outcomes;
    }

    private BulkStatusOutcome checkSingle(TransactionType transactionType,
            UUID documentId, UUID userId) {
        if (transactionType == TransactionType.INVOICE) {
            return checkInvoice(documentId, userId);
        }
        return checkReceipt(documentId, userId);
    }

    private BulkStatusOutcome checkInvoice(UUID documentId, UUID userId) {
        var opt = invoiceHeaderRepository.findById(documentId);
        if (opt.isEmpty()) {
            return new BulkStatusOutcome(documentId, "NOT_FOUND",
                    null, null, null, null);
        }
        EtaInvoiceHeader header = opt.get();

        if (!checkPermission(userId, header.getCompanyId(),
                header.getAuthorityEnvironmentId(),
                TransactionType.INVOICE, "REFRESH")) {
            return new BulkStatusOutcome(documentId, "FORBIDDEN",
                    null, null, null, null);
        }

        EtaInvoiceState before = header.getState();
        EtaInvoiceState afterState = before;
        String etaResultCode = null;
        String errorSummary = null;
        String outcome = "UNCHANGED";

        try {
            header = orchestrator.checkStatus(header);
            afterState = header.getState();
            if (afterState != before) {
                outcome = "UPDATED";
                etaResultCode = afterState.name();
            } else {
                etaResultCode = "NO_CHANGE";
            }
        } catch (Exception e) {
            outcome = "ETA_ERROR";
            etaResultCode = "ERROR";
            errorSummary = e.getMessage();
        }

        return new BulkStatusOutcome(documentId, outcome,
                before.name(), afterState.name(), etaResultCode,
                errorSummary);
    }

    private BulkStatusOutcome checkReceipt(UUID documentId, UUID userId) {
        var opt = receiptHeaderRepository.findById(documentId);
        if (opt.isEmpty()) {
            return new BulkStatusOutcome(documentId, "NOT_FOUND",
                    null, null, null, null);
        }
        EtaReceiptHeader header = opt.get();

        if (!checkPermission(userId, header.getCompanyId(),
                header.getAuthorityEnvironmentId(),
                TransactionType.RECEIPT, "REFRESH")) {
            return new BulkStatusOutcome(documentId, "FORBIDDEN",
                    null, null, null, null);
        }

        EtaReceiptState before = header.getState();
        EtaReceiptState afterState = before;
        String etaResultCode = null;
        String errorSummary = null;
        String outcome = "UNCHANGED";

        try {
            header = orchestrator.checkReceiptStatus(header);
            afterState = header.getState();
            if (afterState != before) {
                outcome = "UPDATED";
                etaResultCode = afterState.name();
            } else {
                etaResultCode = "NO_CHANGE";
            }
        } catch (Exception e) {
            outcome = "ETA_ERROR";
            etaResultCode = "ERROR";
            errorSummary = e.getMessage();
        }

        return new BulkStatusOutcome(documentId, outcome,
                before.name(), afterState.name(), etaResultCode,
                errorSummary);
    }

    private boolean checkPermission(UUID userId, UUID companyId, short authEnvId,
            TransactionType transactionType, String action) {
        if (userId == null) {
            return false;
        }
        if (TenantContext.isSuperUser()
                && TenantContext.getMode() == TenantContext.Mode.OPERATIONAL_MODE) {
            return true;
        }
        return permissionService.hasPermission(
                userId, companyId, authEnvId,
                transactionType.name(), action);
    }

    public record BulkStatusOutcome(UUID documentId, String outcome,
            String beforeState, String afterState,
            String etaResultCode, String errorSummary) {}
}
