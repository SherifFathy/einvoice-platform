package com.einvoice.api.invoice;

import com.einvoice.api.invoice.dto.SubmissionAttemptResponse;
import com.einvoice.api.invoice.dto.SubmitResponse;
import com.einvoice.api.invoice.dto.ValidationResultResponse;
import com.einvoice.api.invoice.dto.ValidationResultResponse.ValidationItem;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.SubmissionAttempt;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.InvoiceRepository;
import com.einvoice.core.service.InvoiceStateMachine;
import com.einvoice.core.service.SubmissionAttemptService;
import com.einvoice.core.service.SubmissionOrchestrator;
import com.einvoice.core.service.SubmissionResultDto;
import com.einvoice.core.service.ValidationService;
import com.einvoice.core.service.validation.ValidationError;
import com.einvoice.core.service.validation.ValidationLayer;
import com.einvoice.eta.client.EtaDocumentClient;
import com.einvoice.eta.polling.EtaStatusPollingService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for invoice validation, submission, and status tracking. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceSubmissionController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceStateMachine stateMachine;
    private final SubmissionOrchestrator submissionOrchestrator;
    private final SubmissionAttemptService attemptService;
    private final ValidationService validationService;
    private final EtaStatusPollingService etaPollingService;
    private final EtaDocumentClient etaDocumentClient;
    private final AuthorityConfigRepository authorityConfigRepository;

    /**
     * Creates the controller with its required collaborators.
     *
     * @param invoiceRepository invoice repository
     * @param stateMachine invoice state machine
     * @param submissionOrchestrator submission orchestrator
     * @param attemptService submission attempt service
     * @param validationService validation service
     * @param etaPollingService ETA polling service
     * @param etaDocumentClient ETA document client
     * @param authorityConfigRepository authority configuration repository
     */
    public InvoiceSubmissionController(InvoiceRepository invoiceRepository,
            InvoiceStateMachine stateMachine,
            SubmissionOrchestrator submissionOrchestrator,
            SubmissionAttemptService attemptService,
            ValidationService validationService,
            EtaStatusPollingService etaPollingService,
            EtaDocumentClient etaDocumentClient,
            AuthorityConfigRepository authorityConfigRepository) {
        this.invoiceRepository = invoiceRepository;
        this.stateMachine = stateMachine;
        this.submissionOrchestrator = submissionOrchestrator;
        this.attemptService = attemptService;
        this.validationService = validationService;
        this.etaPollingService = etaPollingService;
        this.etaDocumentClient = etaDocumentClient;
        this.authorityConfigRepository = authorityConfigRepository;
    }

    /**
     * Validates an invoice against authority compliance rules.
     *
     * @param id the invoice UUID
     * @return validation result with errors and warnings
     */
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('UPDATE')")
    public ResponseEntity<ValidationResultResponse> validate(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        if (invoice.getStatus() != InvoiceStatus.DRAFT
                && invoice.getStatus() != InvoiceStatus.VALIDATED) {
            return ResponseEntity.status(409)
                    .body(new ValidationResultResponse(false,
                            List.of(new ValidationItem(ValidationLayer.STRUCTURAL, null, null,
                                    "status", "Invoice must be in DRAFT status to validate", "ERROR")),
                            List.of()));
        }

        ValidationService.ValidationResult result = validationService.validate(
                invoice, invoice.getAuthority());

        List<ValidationItem> errorItems = result.errors().stream()
                .map(this::toValidationItem)
                .toList();
        List<ValidationItem> warningItems = result.warnings().stream()
                .map(this::toValidationItem)
                .toList();

        if (!result.hasErrors()) {
            stateMachine.transition(invoice, InvoiceStatus.VALIDATED);
            invoiceRepository.save(invoice);
        }

        return ResponseEntity.ok(new ValidationResultResponse(
                !result.hasErrors(), errorItems, warningItems));
    }

    /**
     * Confirms an invoice is ready for submission.
     *
     * @param id the invoice UUID
     * @return confirmation with updated status
     */
    @PostMapping("/{id}/confirm-submission")
    @PreAuthorize("hasAuthority('UPDATE')")
    public ResponseEntity<Map<String, String>> confirmSubmission(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        if (invoice.getStatus() != InvoiceStatus.VALIDATED) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Invoice must be in VALIDATED status"));
        }

        stateMachine.transition(invoice, InvoiceStatus.READY_FOR_SUBMISSION);
        invoiceRepository.save(invoice);

        return ResponseEntity.ok(Map.of(
                "invoiceId", id.toString(),
                "status", InvoiceStatus.READY_FOR_SUBMISSION.name()));
    }

    /**
     * Submits an invoice to the tax authority.
     *
     * @param id the invoice UUID
     * @return submission result with status and attempt details
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<SubmitResponse> submit(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        if (invoice.getStatus() != InvoiceStatus.READY_FOR_SUBMISSION) {
            return ResponseEntity.status(409).body(new SubmitResponse(
                    id.toString(), invoice.getStatus().name(), 0,
                    invoice.getAuthority().name(), List.of(),
                    List.of(new SubmitResponse.ErrorDetail("STATUS",
                            "Invoice must be in READY_FOR_SUBMISSION status")),
                    null, null));
        }

        SubmissionResultDto result = submissionOrchestrator.submit(id);

        invoiceRepository.findByIdAndCompanyId(id, companyId)
                .ifPresent(updated -> invoice.setStatus(updated.getStatus()));
        Invoice updated = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow();
        List<SubmissionAttempt> attempts = attemptService.getAttempts(id);
        if (attempts.isEmpty()) {
            return ResponseEntity.status(500).body(new SubmitResponse(
                    id.toString(), updated.getStatus().name(), 0,
                    invoice.getAuthority().name(), List.of(),
                    List.of(new SubmitResponse.ErrorDetail("ERROR",
                            "No submission attempt recorded")),
                    null, null));
        }
        SubmissionAttempt latest = attempts.get(attempts.size() - 1);

        return ResponseEntity.ok(new SubmitResponse(
                id.toString(),
                updated.getStatus().name(),
                latest.getAttemptNumber(),
                invoice.getAuthority().name(),
                result.warnings(),
                result.errors().stream()
                        .map(e -> new SubmitResponse.ErrorDetail("ERROR", e))
                        .toList(),
                latest.getSubmittedAt(),
                latest.getCompletedAt()));
    }

    /**
     * Retrieves submission history for an invoice.
     *
     * @param id the invoice UUID
     * @return submission attempts with current status
     */
    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Map<String, Object>> getSubmissions(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        List<SubmissionAttempt> attempts = attemptService.getAttempts(id);
        List<SubmissionAttemptResponse> attemptResponses = attempts.stream()
                .map(a -> new SubmissionAttemptResponse(
                        a.getAttemptNumber(),
                        a.getAuthority().name(),
                        a.getEnvironment().name(),
                        a.getResult().name(),
                        a.getStatusCode(),
                        a.getErrorSummary(),
                        a.getSubmittedAt(),
                        a.getCompletedAt()))
                .toList();

        return ResponseEntity.ok(Map.of(
                "invoiceId", id.toString(),
                "currentStatus", invoice.getStatus().name(),
                "attempts", attemptResponses));
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private ValidationItem toValidationItem(ValidationError ve) {
        return new ValidationItem(
                ve.layer(),
                ve.authority() != null ? ve.authority().name() : null,
                ve.ruleId(),
                ve.field(),
                ve.message(),
                ve.severity().name());
    }

    /**
     * Triggers an on-demand ETA status check for the given invoice.
     *
     * @param id the invoice UUID
     * @return the latest status information
     */
    @PostMapping("/{id}/check-status")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Map<String, Object>> checkStatus(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        if (invoice.getStatus() != InvoiceStatus.IN_REVIEW) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Invoice must be in IN_REVIEW status"));
        }

        if (invoice.getAuthority() != Authority.ETA) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Status check is only available for ETA invoices"));
        }

        AuthorityConfig config = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(
                        invoice.getBranch().getId(),
                        Authority.ETA,
                        invoice.getEnvironment())
                .orElseThrow(() -> new IllegalArgumentException("ETA config not found"));

        InvoiceStatus previousStatus = invoice.getStatus();
        InvoiceStatus newStatus = etaPollingService.checkSingleInvoiceStatus(invoice, config);

        return ResponseEntity.ok(Map.of(
                "invoiceId", id.toString(),
                "status", newStatus.name(),
                "previousStatus", previousStatus.name(),
                "checkedAt", java.time.OffsetDateTime.now().toString()));
    }

    /**
     * Retries a failed-retryable submission with exponential backoff.
     *
     * @param id the invoice UUID
     * @return submission result with status and attempt details
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<SubmitResponse> retry(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        if (invoice.getStatus() != InvoiceStatus.FAILED_RETRYABLE) {
            return ResponseEntity.status(409).body(new SubmitResponse(
                    id.toString(), invoice.getStatus().name(), 0,
                    invoice.getAuthority().name(), List.of(),
                    List.of(new SubmitResponse.ErrorDetail("STATUS",
                            "Invoice must be in FAILED_RETRYABLE status to retry")),
                    null, null));
        }

        SubmissionResultDto result = submissionOrchestrator.retry(id);

        Invoice updated = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow();
        List<SubmissionAttempt> attempts = attemptService.getAttempts(id);
        SubmissionAttempt latest = attempts.get(attempts.size() - 1);

        return ResponseEntity.ok(new SubmitResponse(
                id.toString(),
                updated.getStatus().name(),
                latest.getAttemptNumber(),
                invoice.getAuthority().name(),
                result.warnings(),
                result.errors().stream()
                        .map(e -> new SubmitResponse.ErrorDetail("ERROR", e))
                        .toList(),
                latest.getSubmittedAt(),
                latest.getCompletedAt()));
    }

    /**
     * Returns a rejected invoice to draft for correction.
     *
     * @param id the invoice UUID
     * @return confirmation with updated status
     */
    @PostMapping("/{id}/return-to-draft")
    @PreAuthorize("hasAuthority('UPDATE')")
    public ResponseEntity<Map<String, String>> returnToDraft(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        if (invoice.getStatus() != InvoiceStatus.REJECTED) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Invoice must be in REJECTED status to return to draft"));
        }

        stateMachine.transition(invoice, InvoiceStatus.DRAFT);
        invoiceRepository.save(invoice);

        return ResponseEntity.ok(Map.of(
                "invoiceId", id.toString(),
                "status", InvoiceStatus.DRAFT.name()));
    }

    /**
     * Cancels an accepted ETA invoice.
     *
     * @param id the invoice UUID
     * @param body optional payload containing cancellation reason
     * @return the cancellation outcome
     */
    @PostMapping("/{id}/cancel-eta")
    @PreAuthorize("hasAuthority('UPDATE')")
    public ResponseEntity<Map<String, Object>> cancelEta(@PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        if (invoice.getStatus() != InvoiceStatus.ACCEPTED) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Invoice must be in ACCEPTED status to cancel"));
        }

        if (invoice.getAuthority() != Authority.ETA) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Cancellation is only available for ETA invoices"));
        }

        String reason = body != null ? body.get("reason") : null;

        AuthorityConfig config = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(
                        invoice.getBranch().getId(),
                        Authority.ETA,
                        invoice.getEnvironment())
                .orElseThrow(() -> new IllegalArgumentException("ETA config not found"));

        String documentId = invoice.getExternalInvoiceReference() != null
                ? invoice.getExternalInvoiceReference()
                : invoice.getInvoiceNumber();

        boolean cancelled = etaDocumentClient.cancelDocument(documentId, reason, config);
        if (!cancelled) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "ETA document cancellation failed"));
        }

        stateMachine.transition(invoice, InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);

        return ResponseEntity.ok(Map.of(
                "invoiceId", id.toString(),
                "status", InvoiceStatus.CANCELLED.name()));
    }
}
