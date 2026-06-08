package com.einvoice.api.zatca.submission;

import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper.ZatcaSimplifiedResponse;
import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedService;
import com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper.ZatcaStandardResponse;
import com.einvoice.api.zatca.standard.service.ZatcaStandardService;
import com.einvoice.api.zatca.submission.service.BulkCheckStatusRunRegistry;
import com.einvoice.api.zatca.submission.service.BulkCheckStatusService;
import com.einvoice.api.zatca.submission.service.ZatcaSubmissionOrchestrator;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.error.UnauthorizedContextException;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.security.operational.RequireOperationalMode;
import com.einvoice.security.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** REST controller for ZATCA Standard and Simplified document submission. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/zatca")
public class ZatcaSubmissionController {

    private final ZatcaSubmissionOrchestrator orchestrator;
    private final ZatcaStandardService standardService;
    private final ZatcaSimplifiedService simplifiedService;
    private final BulkCheckStatusService bulkCheckStatusService;
    private final BulkCheckStatusRunRegistry bulkRunRegistry;

    /**
     * Constructs the ZATCA submission controller.
     *
     * @param orchestrator submission orchestrator
     * @param standardService ZATCA standard service
     * @param simplifiedService ZATCA simplified service
     * @param bulkCheckStatusService bulk status-check service
     * @param bulkRunRegistry bulk run registry
     */
    public ZatcaSubmissionController(
            ZatcaSubmissionOrchestrator orchestrator,
            ZatcaStandardService standardService,
            ZatcaSimplifiedService simplifiedService,
            BulkCheckStatusService bulkCheckStatusService,
            BulkCheckStatusRunRegistry bulkRunRegistry) {
        this.orchestrator = orchestrator;
        this.standardService = standardService;
        this.simplifiedService = simplifiedService;
        this.bulkCheckStatusService = bulkCheckStatusService;
        this.bulkRunRegistry = bulkRunRegistry;
    }

    /**
     * Submit a Standard (B2B) document for ZATCA clearance.
     *
     * @param companyId the company context
     * @param docId the document to submit
     * @return submission outcome with state, clearance status, and attempt
     */
    @PostMapping("/standard/{docId}/submit")
    @RequiresPermission(transactionType = "STANDARD", action = "SUBMIT")
    public ResponseEntity<Map<String, Object>> submitStandard(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        ZatcaStandardHeader header = standardService.loadWithinTenant(
                docId);
        var outcome = orchestrator.submitStandard(header);
        ZatcaStandardResponse response =
                com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper
                        .toResponse(outcome.header());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", response.status().name());
        body.put("clearanceStatus", response.clearanceStatus() != null
                ? response.clearanceStatus() : "");
        body.put("attempt", toAttemptMap(outcome.attempt()));
        body.put("document", response);
        return ResponseEntity.ok(body);
    }

    /**
     * Submit a Simplified (B2C) document for ZATCA reporting.
     *
     * @param companyId the company context
     * @param docId the document to submit
     * @return submission outcome with state, reporting status, and attempt
     */
    @PostMapping("/simplified/{docId}/submit")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "SUBMIT")
    public ResponseEntity<Map<String, Object>> submitSimplified(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        ZatcaSimplifiedHeader header =
                simplifiedService.loadWithinTenant(docId);
        var outcome = orchestrator.submitSimplified(header);
        ZatcaSimplifiedResponse response =
                com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper
                        .toResponse(outcome.header());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", response.status().name());
        body.put("reportingStatus", response.reportingStatus() != null
                ? response.reportingStatus() : "");
        body.put("attempt", toAttemptMap(outcome.attempt()));
        body.put("document", response);
        return ResponseEntity.ok(body);
    }

    /**
     * Cancel a Standard document.
     *
     * @param companyId the company context
     * @param docId the document to cancel
     * @param body request body carrying the cancel {@code reason}
     * @return cancel outcome with state, clearance status, and attempt
     */
    @PostMapping("/standard/{docId}/cancel")
    @RequiresPermission(transactionType = "STANDARD", action = "CANCEL")
    public ResponseEntity<Map<String, Object>> cancelStandard(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestBody Map<String, String> body) {
        verifyContext(companyId);
        String reason = validateCancelReason(body.get("reason"));
        ZatcaStandardHeader header = standardService.loadWithinTenant(
                docId);
        var outcome = orchestrator.cancelStandard(header, reason);
        ZatcaStandardResponse response =
                com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper
                        .toResponse(outcome.header());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", response.status().name());
        result.put("clearanceStatus",
                response.clearanceStatus() != null
                        ? response.clearanceStatus() : "");
        result.put("attempt", toAttemptMap(outcome.attempt()));
        result.put("document", response);
        return ResponseEntity.ok(result);
    }

    /**
     * Retry a failed Standard submission.
     *
     * @param companyId the company context
     * @param docId the document to retry
     * @return retry outcome with state, clearance status, and attempt
     */
    @PostMapping("/standard/{docId}/retry")
    @RequiresPermission(transactionType = "STANDARD", action = "SUBMIT")
    public ResponseEntity<Map<String, Object>> retryStandard(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        ZatcaStandardHeader header = standardService.loadWithinTenant(
                docId);
        var outcome = orchestrator.retryStandard(header);
        ZatcaStandardResponse response =
                com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper
                        .toResponse(outcome.header());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", response.status().name());
        result.put("clearanceStatus",
                response.clearanceStatus() != null
                        ? response.clearanceStatus() : "");
        result.put("attempt", toAttemptMap(outcome.attempt()));
        result.put("document", response);
        return ResponseEntity.ok(result);
    }

    /**
     * Refresh the ZATCA clearance status of a Standard document.
     *
     * @param companyId the company context
     * @param docId the document to refresh
     * @return status outcome with state, clearance status, and attempt
     */
    @PostMapping("/standard/{docId}/check-status")
    @RequiresPermission(transactionType = "STANDARD", action = "REFRESH")
    public ResponseEntity<Map<String, Object>> checkStandardStatus(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        ZatcaStandardHeader header = standardService.loadWithinTenant(
                docId);
        var outcome = orchestrator.checkStandardStatus(header);
        ZatcaStandardResponse response =
                com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper
                        .toResponse(outcome.header());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", response.status().name());
        result.put("clearanceStatus",
                response.clearanceStatus() != null
                        ? response.clearanceStatus() : "");
        result.put("attempt", toAttemptMap(outcome.attempt()));
        result.put("document", response);
        return ResponseEntity.ok(result);
    }

    /**
     * Cancel a Simplified document.
     *
     * @param companyId the company context
     * @param docId the document to cancel
     * @param body request body carrying the cancel {@code reason}
     * @return cancel outcome with state, reporting status, and attempt
     */
    @PostMapping("/simplified/{docId}/cancel")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "CANCEL")
    public ResponseEntity<Map<String, Object>> cancelSimplified(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestBody Map<String, String> body) {
        verifyContext(companyId);
        String reason = validateCancelReason(body.get("reason"));
        ZatcaSimplifiedHeader header =
                simplifiedService.loadWithinTenant(docId);
        var outcome = orchestrator.cancelSimplified(header, reason);
        ZatcaSimplifiedResponse response =
                com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper
                        .toResponse(outcome.header());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", response.status().name());
        result.put("reportingStatus",
                response.reportingStatus() != null
                        ? response.reportingStatus() : "");
        result.put("attempt", toAttemptMap(outcome.attempt()));
        result.put("document", response);
        return ResponseEntity.ok(result);
    }

    /**
     * Retry a failed Simplified submission.
     *
     * @param companyId the company context
     * @param docId the document to retry
     * @return retry outcome with state, reporting status, and attempt
     */
    @PostMapping("/simplified/{docId}/retry")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "SUBMIT")
    public ResponseEntity<Map<String, Object>> retrySimplified(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        ZatcaSimplifiedHeader header =
                simplifiedService.loadWithinTenant(docId);
        var outcome = orchestrator.retrySimplified(header);
        ZatcaSimplifiedResponse response =
                com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper
                        .toResponse(outcome.header());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", response.status().name());
        result.put("reportingStatus",
                response.reportingStatus() != null
                        ? response.reportingStatus() : "");
        result.put("attempt", toAttemptMap(outcome.attempt()));
        result.put("document", response);
        return ResponseEntity.ok(result);
    }

    /**
     * Refresh the ZATCA reporting status of a Simplified document.
     *
     * @param companyId the company context
     * @param docId the document to refresh
     * @return status outcome with state, reporting status, and attempt
     */
    @PostMapping("/simplified/{docId}/check-status")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "REFRESH")
    public ResponseEntity<Map<String, Object>> checkSimplifiedStatus(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        ZatcaSimplifiedHeader header =
                simplifiedService.loadWithinTenant(docId);
        var outcome = orchestrator.checkSimplifiedStatus(header);
        ZatcaSimplifiedResponse response =
                com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper
                        .toResponse(outcome.header());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", response.status().name());
        result.put("reportingStatus",
                response.reportingStatus() != null
                        ? response.reportingStatus() : "");
        result.put("attempt", toAttemptMap(outcome.attempt()));
        result.put("document", response);
        return ResponseEntity.ok(result);
    }

    /**
     * Bulk-refresh the clearance status of multiple Standard documents,
     * streaming NDJSON results.
     *
     * @param companyId the company context
     * @param body request body carrying the {@code documentIds} list
     * @return a streaming NDJSON response of per-document outcomes
     */
    @PostMapping("/standard/check-status")
    @RequiresPermission(transactionType = "STANDARD", action = "REFRESH")
    public ResponseEntity<StreamingResponseBody> bulkCheckStandardStatus(
            @PathVariable UUID companyId,
            @RequestBody Map<String, List<UUID>> body) {
        verifyContext(companyId);
        List<UUID> documentIds = body.get("documentIds");
        BulkCheckStatusRunRegistry.RunEntry run =
                bulkRunRegistry.createRun();
        TenantContext.Holder capturedContext = TenantContext.current();
        StreamingResponseBody stream = out -> {
            bulkCheckStatusService.runWithExistingRun(run,
                    capturedContext, documentIds,
                    TransactionType.STANDARD, out);
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/x-ndjson"))
                .header("Run-Id", run.runId)
                .body(stream);
    }

    /**
     * Bulk-refresh the reporting status of multiple Simplified documents,
     * streaming NDJSON results.
     *
     * @param companyId the company context
     * @param body request body carrying the {@code documentIds} list
     * @return a streaming NDJSON response of per-document outcomes
     */
    @PostMapping("/simplified/check-status")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "REFRESH")
    public ResponseEntity<StreamingResponseBody> bulkCheckSimplifiedStatus(
            @PathVariable UUID companyId,
            @RequestBody Map<String, List<UUID>> body) {
        verifyContext(companyId);
        List<UUID> documentIds = body.get("documentIds");
        BulkCheckStatusRunRegistry.RunEntry run =
                bulkRunRegistry.createRun();
        TenantContext.Holder capturedContext = TenantContext.current();
        StreamingResponseBody stream = out -> {
            bulkCheckStatusService.runWithExistingRun(run,
                    capturedContext, documentIds,
                    TransactionType.SIMPLIFIED, out);
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/x-ndjson"))
                .header("Run-Id", run.runId)
                .body(stream);
    }

    private Map<String, Object> toAttemptMap(SubmissionAttempt a) {
        if (a == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("attemptNumber", a.getAttemptNumber());
        m.put("chainCounterSnapshot", a.getChainCounterSnapshot());
        m.put("result", a.getResult() != null ? a.getResult().name() : null);
        m.put("errorSummary", a.getErrorSummary());
        m.put("submittedBy", a.getSubmittedBy() != null
                ? a.getSubmittedBy().toString() : null);
        m.put("startedAt", a.getSubmittedAt() != null
                ? a.getSubmittedAt().toString() : null);
        m.put("finalisedAt", a.getCompletedAt() != null
                ? a.getCompletedAt().toString() : null);
        return m;
    }

    private String validateCancelReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new org.springframework.web.server
                    .ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Cancel reason is required");
        }
        return reason;
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
