package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.SubmissionAttempt;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.domain.enums.Permission;
import com.einvoice.core.domain.enums.SubmissionResult;
import com.einvoice.core.exception.InvalidTransitionException;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.InvoiceRepository;
import com.einvoice.core.security.RequiresPermission;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates the multi-step invoice submission flow to tax authorities. */
@Service
public class SubmissionOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(SubmissionOrchestrator.class);

    static final int MAX_RETRIES = 3;
    static final long[] BACKOFF_BASE_MS = {2000, 4000, 8000};
    static final long JITTER_RANGE_MS = 500;
    static final long AUTHORITY_TIMEOUT_S = 30;

    private final InvoiceRepository invoiceRepository;
    private final AuthorityConfigRepository authorityConfigRepository;
    private final InvoiceStateMachine stateMachine;
    private final AuthorityEngineFactory engineFactory;
    private final InvoiceArtifactService artifactService;
    private final SubmissionAttemptService attemptService;
    private final AuditService auditService;

    /**
     * Constructs the orchestrator with required dependencies.
     *
     * @param invoiceRepository invoice data access
     * @param authorityConfigRepository authority config data access
     * @param stateMachine state machine for transitions
     * @param engineFactory factory for authority engines
     * @param artifactService artifact storage service
     * @param attemptService attempt record service
     * @param auditService audit logging service
     */
    public SubmissionOrchestrator(InvoiceRepository invoiceRepository,
                                  AuthorityConfigRepository authorityConfigRepository,
                                  InvoiceStateMachine stateMachine,
                                  AuthorityEngineFactory engineFactory,
                                  InvoiceArtifactService artifactService,
                                  SubmissionAttemptService attemptService,
                                  AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.authorityConfigRepository = authorityConfigRepository;
        this.stateMachine = stateMachine;
        this.engineFactory = engineFactory;
        this.artifactService = artifactService;
        this.attemptService = attemptService;
        this.auditService = auditService;
    }

    /**
     * Submits an invoice to the appropriate authority engine.
     *
     * @param invoiceId the invoice UUID
     * @return the submission result
     */
    @Transactional
    @RequiresPermission(Permission.TRANSFER_INVOICE)
    @Audited(action = "invoice.submit", entityType = "Invoice",
            entityClass = Invoice.class)
    public SubmissionResultDto submit(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invoice not found: " + invoiceId));

        Authority authority = invoice.getAuthority();

        stateMachine.transition(invoice, InvoiceStatus.SUBMISSION_IN_PROGRESS);
        invoiceRepository.saveAndFlush(invoice);

        return doSubmit(invoice, authority);
    }

    /**
     * Retries a failed-retryable submission with exponential backoff and jitter.
     *
     * @param invoiceId the invoice UUID
     * @return the submission result
     */
    @Transactional
    @RequiresPermission(Permission.REFRESH_INVOICE)
    @Audited(action = "invoice.retry", entityType = "Invoice",
            entityClass = Invoice.class)
    public SubmissionResultDto retry(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invoice not found: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.FAILED_RETRYABLE) {
            throw new IllegalStateException(
                    "Invoice must be in FAILED_RETRYABLE status to retry, current: "
                            + invoice.getStatus());
        }

        int attemptCount = attemptService.getNextAttemptNumber(invoiceId) - 1;
        if (attemptCount >= MAX_RETRIES) {
            stateMachine.transition(invoice,
                    InvoiceStatus.FAILED_NON_RETRYABLE);
            invoiceRepository.save(invoice);
            auditService.log("RETRY_EXHAUSTED", "Invoice",
                    invoiceId.toString(),
                    InvoiceStatus.FAILED_RETRYABLE.name(),
                    InvoiceStatus.FAILED_NON_RETRYABLE.name(),
                    invoice.getCompany().getId());
            return SubmissionResultDto.error(
                    "Maximum retry attempts (" + MAX_RETRIES + ") exceeded");
        }

        long backoff = computeBackoff(attemptCount);
        log.info("Retrying invoice {} (attempt {}/{}), backoff {}ms",
                invoiceId, attemptCount + 1, MAX_RETRIES, backoff);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SubmissionResultDto.error("Retry interrupted");
        }

        stateMachine.transition(invoice, InvoiceStatus.SUBMISSION_IN_PROGRESS);
        invoiceRepository.saveAndFlush(invoice);

        return doSubmit(invoice, invoice.getAuthority());
    }

    private SubmissionResultDto doSubmit(Invoice invoice, Authority authority) {
        UUID invoiceId = invoice.getId();

        AuthorityConfig config = authorityConfigRepository
                .findWithLockByBranchIdAndAuthorityAndEnvironment(
                        invoice.getBranch().getId(),
                        authority,
                        invoice.getEnvironment())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authority config not found"));

        int attemptNumber = attemptService.getNextAttemptNumber(invoiceId);
        SubmissionAttempt attempt = attemptService.createAttempt(
                invoice, attemptNumber, config);

        try {
            AuthorityEngine engine = engineFactory.getEngine(authority);

            String payload = engine.generatePayload(invoice, config);
            artifactService.storeArtifact(
                    invoice, engine.getPayloadArtifactType(), payload);

            SubmissionResultDto result = submitWithTimeout(engine, invoice, payload, config);

            if (result.authorityResponse() != null) {
                artifactService.storeArtifact(invoice,
                        engine.getResponseArtifactType(),
                        result.authorityResponse());
            }
            if (result.clearedDocument() != null) {
                artifactService.storeArtifact(invoice,
                        engine.getClearedArtifactType(),
                        result.clearedDocument());
            }

            result.generatedArtifacts().forEach((type, content) ->
                    artifactService.storeArtifact(invoice, type, content));

            updateInvoiceState(invoice, result);
            attemptService.completeAttempt(attempt, result);

            if (result.status() == SubmissionResult.SUCCESS) {
                updateHashChain(config, engine, payload);
            }

            auditService.log("SUBMISSION_COMPLETE", "Invoice",
                    invoiceId.toString(),
                    InvoiceStatus.SUBMISSION_IN_PROGRESS.name(),
                    invoice.getStatus().name(),
                    invoice.getCompany().getId());

            return result;

        } catch (InvalidTransitionException e) {
            log.warn("Invalid state transition for invoice {}: {}",
                    invoiceId, e.getMessage());
            attemptService.completeAttempt(attempt,
                    SubmissionResultDto.error(e.getMessage()));
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during submission for invoice {}",
                    invoiceId, e);
            attemptService.completeAttempt(attempt,
                    SubmissionResultDto.error(e.getMessage()));
            try {
                stateMachine.transition(invoice,
                        InvoiceStatus.FAILED_NON_RETRYABLE);
                invoiceRepository.save(invoice);
            } catch (InvalidTransitionException ex) {
                log.warn("Could not transition to FAILED_NON_RETRYABLE: {}",
                        ex.getMessage());
            }
            return SubmissionResultDto.error(e.getMessage());
        }
    }

    private SubmissionResultDto submitWithTimeout(AuthorityEngine engine,
                                                   Invoice invoice,
                                                   String payload,
                                                   AuthorityConfig config) {
        OffsetDateTime deadline = OffsetDateTime.now()
                .plus(AUTHORITY_TIMEOUT_S, ChronoUnit.SECONDS);
        try {
            SubmissionResultDto result = engine.submit(invoice, payload, config);
            if (OffsetDateTime.now().isAfter(deadline)) {
                log.warn("Authority responded after timeout for invoice {}",
                        invoice.getId());
                return SubmissionResultDto.ambiguous();
            }
            return result;
        } catch (Exception e) {
            log.error("Authority submission failed for invoice {}",
                    invoice.getId(), e);
            if (OffsetDateTime.now().isAfter(deadline)) {
                return SubmissionResultDto.ambiguous();
            }
            return SubmissionResultDto.timeout();
        }
    }

    static long computeBackoff(int retryIndex) {
        int idx = Math.min(retryIndex, BACKOFF_BASE_MS.length - 1);
        long base = BACKOFF_BASE_MS[idx];
        long jitter = ThreadLocalRandom.current()
                .nextLong(-JITTER_RANGE_MS, JITTER_RANGE_MS + 1);
        return Math.max(0, base + jitter);
    }

    private void updateInvoiceState(Invoice invoice,
                                    SubmissionResultDto result) {
        InvoiceStatus target = resolveTargetStatus(invoice, result);
        stateMachine.transition(invoice, target);
        invoiceRepository.save(invoice);
    }

    private InvoiceStatus resolveTargetStatus(
            Invoice invoice, SubmissionResultDto result) {
        if (result.status() == SubmissionResult.SUCCESS) {
            if (invoice.getAuthority() == Authority.ZATCA) {
                return invoice.getType() == InvoiceType.SIMPLIFIED_TAX_INVOICE
                        ? InvoiceStatus.REPORTED
                        : InvoiceStatus.CLEARED;
            }
            return InvoiceStatus.IN_REVIEW;
        }
        if (result.status() == SubmissionResult.REJECTED) {
            return InvoiceStatus.REJECTED;
        }
        if (result.status() == SubmissionResult.TIMEOUT
                || result.status() == SubmissionResult.AMBIGUOUS) {
            return InvoiceStatus.SUBMISSION_AMBIGUOUS;
        }
        return InvoiceStatus.FAILED_RETRYABLE;
    }

    private void updateHashChain(AuthorityConfig config,
                                 AuthorityEngine engine, String payload) {
        String newHash = engine.computeInvoiceHash(payload);
        if (newHash != null && !newHash.isBlank()) {
            config.setPreviousInvoiceHash(newHash);
        }
        long current = config.getInvoiceCounter() != null ? config.getInvoiceCounter() : 0L;
        config.setInvoiceCounter(current + 1);
        authorityConfigRepository.save(config);
    }
}
