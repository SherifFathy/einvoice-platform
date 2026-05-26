package com.einvoice.api.eta.submission;

import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceResponse;
import com.einvoice.api.eta.invoice.service.EtaInvoiceService;
import com.einvoice.api.eta.receipt.service.EtaReceiptService;
import com.einvoice.api.eta.submission.service.BulkStatusCheckExecutor;
import com.einvoice.api.eta.submission.service.BulkStatusCheckExecutor.BulkStatusOutcome;
import com.einvoice.api.eta.submission.service.EtaSubmissionOrchestrator;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.error.UnauthorizedContextException;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.security.operational.RequireOperationalMode;
import com.einvoice.security.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for ETA document submission, retry, cancel and bulk-status endpoints. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/eta")
public class EtaSubmissionController {

    private final EtaSubmissionOrchestrator orchestrator;
    private final EtaInvoiceService invoiceService;
    private final EtaReceiptService receiptService;
    private final BulkStatusCheckExecutor bulkExecutor;

    /**
     * Constructs an EtaSubmissionController.
     *
     * @param orchestrator the submission orchestrator
     * @param invoiceService the invoice service
     * @param receiptService the receipt service
     * @param bulkExecutor the bulk status check executor
     */
    public EtaSubmissionController(
            EtaSubmissionOrchestrator orchestrator,
            EtaInvoiceService invoiceService,
            EtaReceiptService receiptService,
            BulkStatusCheckExecutor bulkExecutor) {
        this.orchestrator = orchestrator;
        this.invoiceService = invoiceService;
        this.receiptService = receiptService;
        this.bulkExecutor = bulkExecutor;
    }

    /**
     * Submits a single invoice to ETA.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return submission result with state and ETA identifiers
     */
    @PostMapping("/invoices/{docId}/submit")
    @RequiresPermission(transactionType = "INVOICE", action = "SUBMIT")
    public ResponseEntity<Map<String, Object>> submitInvoice(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        EtaInvoiceHeader header = invoiceService.loadWithinTenant(docId);
        header = orchestrator.submit(header);
        return ResponseEntity.ok(Map.of(
                "state", header.getState().name(),
                "etaUuid", header.getEtaUuid() != null
                        ? header.getEtaUuid() : "",
                "etaSubmissionId",
                        header.getEtaSubmissionId() != null
                                ? header.getEtaSubmissionId() : ""));
    }

    /**
     * Retries a failed invoice submission to ETA.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return submission result with state and ETA identifiers
     */
    @PostMapping("/invoices/{docId}/retry")
    @RequiresPermission(transactionType = "INVOICE", action = "SUBMIT")
    public ResponseEntity<Map<String, Object>> retryInvoice(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        EtaInvoiceHeader header = invoiceService.loadWithinTenant(docId);
        header = orchestrator.retry(header);
        return ResponseEntity.ok(Map.of(
                "state", header.getState().name(),
                "etaUuid", header.getEtaUuid() != null
                        ? header.getEtaUuid() : "",
                "etaSubmissionId",
                        header.getEtaSubmissionId() != null
                                ? header.getEtaSubmissionId() : ""));
    }

    /**
     * Cancels a previously submitted invoice.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @param body request body containing the cancellation reason
     * @return cancellation result with updated state
     */
    @PostMapping("/invoices/{docId}/cancel")
    @RequiresPermission(transactionType = "INVOICE", action = "CANCEL")
    public ResponseEntity<Map<String, Object>> cancelInvoice(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestBody Map<String, String> body) {
        verifyContext(companyId);
        EtaInvoiceHeader header = invoiceService.loadWithinTenant(docId);
        header = orchestrator.cancel(header, body.get("reason"));
        return ResponseEntity.ok(Map.of(
                "state", header.getState().name()));
    }

    /**
     * Bulk-checks the submission status of multiple invoices.
     *
     * @param companyId the company identifier
     * @param body request body containing the list of document IDs
     * @return bulk status check results
     */
    @PostMapping("/invoices/check-status")
    @RequiresPermission(transactionType = "INVOICE", action = "REFRESH")
    public ResponseEntity<Map<String, Object>> bulkCheckInvoiceStatus(
            @PathVariable UUID companyId,
            @RequestBody Map<String, List<UUID>> body) {
        verifyContext(companyId);
        List<UUID> documentIds = body.get("documentIds");
        List<BulkStatusOutcome> outcomes =
                bulkExecutor.runBulk(TransactionType.INVOICE, documentIds);
        return ResponseEntity.ok(Map.of("results", outcomes));
    }

    /**
     * Submits a single receipt to ETA.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return submission result with state and ETA identifiers
     */
    @PostMapping("/receipts/{docId}/submit")
    @RequiresPermission(transactionType = "RECEIPT", action = "SUBMIT")
    public ResponseEntity<Map<String, Object>> submitReceipt(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        EtaReceiptHeader header = receiptService.loadWithinTenant(docId);
        header = orchestrator.submitReceipt(header);
        return ResponseEntity.ok(Map.of(
                "state", header.getState().name(),
                "etaReceiptUuid", header.getEtaReceiptUuid() != null
                        ? header.getEtaReceiptUuid() : "",
                "etaSubmissionId",
                        header.getEtaSubmissionId() != null
                                ? header.getEtaSubmissionId() : ""));
    }

    /**
     * Retries a failed receipt submission to ETA.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return submission result with state and ETA identifiers
     */
    @PostMapping("/receipts/{docId}/retry")
    @RequiresPermission(transactionType = "RECEIPT", action = "SUBMIT")
    public ResponseEntity<Map<String, Object>> retryReceipt(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        EtaReceiptHeader header = receiptService.loadWithinTenant(docId);
        header = orchestrator.retryReceipt(header);
        return ResponseEntity.ok(Map.of(
                "state", header.getState().name(),
                "etaReceiptUuid", header.getEtaReceiptUuid() != null
                        ? header.getEtaReceiptUuid() : "",
                "etaSubmissionId",
                        header.getEtaSubmissionId() != null
                                ? header.getEtaSubmissionId() : ""));
    }

    /**
     * Cancels a previously submitted receipt.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @param body request body containing the cancellation reason
     * @return cancellation result with updated state
     */
    @PostMapping("/receipts/{docId}/cancel")
    @RequiresPermission(transactionType = "RECEIPT", action = "CANCEL")
    public ResponseEntity<Map<String, Object>> cancelReceipt(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestBody Map<String, String> body) {
        verifyContext(companyId);
        EtaReceiptHeader header = receiptService.loadWithinTenant(docId);
        header = orchestrator.cancelReceipt(header, body.get("reason"));
        return ResponseEntity.ok(Map.of(
                "state", header.getState().name()));
    }

    /**
     * Bulk-checks the submission status of multiple receipts.
     *
     * @param companyId the company identifier
     * @param body request body containing the list of document IDs
     * @return bulk status check results
     */
    @PostMapping("/receipts/check-status")
    @RequiresPermission(transactionType = "RECEIPT", action = "REFRESH")
    public ResponseEntity<Map<String, Object>> bulkCheckReceiptStatus(
            @PathVariable UUID companyId,
            @RequestBody Map<String, List<UUID>> body) {
        verifyContext(companyId);
        List<UUID> documentIds = body.get("documentIds");
        List<BulkStatusOutcome> outcomes =
                bulkExecutor.runBulk(TransactionType.RECEIPT, documentIds);
        return ResponseEntity.ok(Map.of("results", outcomes));
    }

    private void verifyContext(UUID pathCompanyId) {
        UUID jwtCompanyId = TenantContext.getCompanyId();
        if (jwtCompanyId == null
                || !jwtCompanyId.equals(pathCompanyId)) {
            throw new UnauthorizedContextException(
                    "Path companyId does not match authenticated"
                            + " company context");
        }
    }
}
