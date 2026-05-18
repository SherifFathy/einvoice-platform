package com.einvoice.api.eta.submission.service;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.CancelInput;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.domain.eta.lifecycle.EtaReceiptState;
import com.einvoice.core.domain.eta.lifecycle.LifecycleAction;
import com.einvoice.core.domain.shared.ArtifactType;
import com.einvoice.core.domain.shared.InvoiceArtifact;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import com.einvoice.core.lifecycle.EtaInvoiceLifecycle;
import com.einvoice.core.lifecycle.EtaReceiptLifecycle;
import com.einvoice.core.repository.eta.EtaInvoiceHeaderRepository;
import com.einvoice.core.repository.eta.EtaReceiptHeaderRepository;
import com.einvoice.core.repository.shared.InvoiceArtifactRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.eta.engine.EtaAuthorityEngine;
import com.einvoice.security.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates ETA invoice submission with split-transaction semantics.
 *
 * <p>The in-flight SubmissionAttempt is committed before the outbound
 * HTTP call so an ambiguous outcome is durable (Research Decision 4).</p>
 */
@Service
public class EtaSubmissionOrchestrator {

    private final EtaAuthorityEngine engine;
    private final SubmissionAttemptRepository attemptRepository;
    private final InvoiceArtifactRepository artifactRepository;
    private final AuditService auditService;
    private final EtaInvoiceHeaderRepository headerRepository;
    private final EtaReceiptHeaderRepository receiptHeaderRepository;
    private final TransactionTemplate txTemplate;

    /**
     * Constructs an EtaSubmissionOrchestrator.
     *
     * @param engine the ETA authority engine
     * @param attemptRepository the submission attempt repository
     * @param artifactRepository the invoice artifact repository
     * @param auditService the audit service
     * @param headerRepository the invoice header repository
     * @param receiptHeaderRepository the receipt header repository
     * @param txTemplate the transaction template for split-transaction control
     */
    public EtaSubmissionOrchestrator(EtaAuthorityEngine engine,
            SubmissionAttemptRepository attemptRepository,
            InvoiceArtifactRepository artifactRepository,
            AuditService auditService,
            EtaInvoiceHeaderRepository headerRepository,
            EtaReceiptHeaderRepository receiptHeaderRepository,
            TransactionTemplate txTemplate) {
        this.engine = engine;
        this.attemptRepository = attemptRepository;
        this.artifactRepository = artifactRepository;
        this.auditService = auditService;
        this.headerRepository = headerRepository;
        this.receiptHeaderRepository = receiptHeaderRepository;
        this.txTemplate = txTemplate;
    }

    /**
     * Submits a DRAFT invoice to the ETA authority.
     *
     * @param header the invoice header (must be DRAFT)
     * @return the updated header with final state
     */
    public EtaInvoiceHeader submit(EtaInvoiceHeader header) {
        return doSubmit(header, LifecycleAction.SUBMIT);
    }

    /**
     * Retries a previously ambiguous submission.
     *
     * @param header the invoice header (must be SUBMISSION_AMBIGUOUS)
     * @return the updated header with final state
     */
    public EtaInvoiceHeader retry(EtaInvoiceHeader header) {
        return doSubmit(header, LifecycleAction.RETRY);
    }

    /**
     * Cancels a previously VALID invoice.
     *
     * @param header the invoice header (must be VALID)
     * @param reason the cancellation reason
     * @return the updated header (CANCELLED)
     */
    public EtaInvoiceHeader cancel(EtaInvoiceHeader header,
            String reason) {
        EtaInvoiceState targetState = EtaInvoiceLifecycle.next(
                header.getState(), LifecycleAction.CANCEL);
        UUID headerId = header.getId();
        UUID companyId = header.getCompanyId();
        Short authEnvId = header.getAuthorityEnvironmentId();

        final UUID[] attemptIdHolder = new UUID[1];
        final int[] attemptNumberHolder = new int[1];

        txTemplate.executeWithoutResult(status -> {
            int next = getNextAttemptNumber(headerId);
            SubmissionAttempt attempt = SubmissionAttempt.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(authEnvId)
                    .transactionType(TransactionType.INVOICE)
                    .documentId(headerId)
                    .attemptNumber(next)
                    .submittedBy(TenantContext.getUserId())
                    .build();
            attempt = attemptRepository.save(attempt);
            attemptIdHolder[0] = attempt.getId();
            attemptNumberHolder[0] = next;
        });

        AuthorityResponse response = engine.cancel(
                new CancelInput(companyId, authEnvId,
                        TransactionType.INVOICE.name(),
                        headerId, header.getEtaUuid(), reason));

        return txTemplate.execute(status -> {
            SubmissionResult result = mapResult(response);
            attemptRepository.finalizeAttempt(attemptIdHolder[0], result,
                    response.statusCode(), response.errorSummary(),
                    null, OffsetDateTime.now());

            EtaInvoiceHeader h = headerRepository.findById(headerId)
                    .orElseThrow();
            if (result == SubmissionResult.SUCCESS) {
                h.setState(targetState);
            }
            if (response.etaUuid() != null) {
                h.setEtaUuid(response.etaUuid());
            }
            if (response.etaLongId() != null) {
                h.setEtaLongId(response.etaLongId());
            }

            if (response.rawResponse() != null) {
                recordArtifactWithAttempt(h, ArtifactType.ETA_RESPONSE,
                        response.rawResponse(), attemptNumberHolder[0]);
            }

            headerRepository.saveAndFlush(h);
            auditService.record("CANCEL_INVOICE", "ETA_INVOICE",
                    headerId.toString(),
                    Map.of("reason", reason != null ? reason : ""),
                    Map.of("state", h.getState().name(),
                            "result", result.name()));
            return h;
        });
    }

    /**
     * Checks the status of a submitted document.
     *
     * @param header the invoice header
     * @return the updated header
     */
    public EtaInvoiceHeader checkStatus(EtaInvoiceHeader header) {
        if (!EtaInvoiceLifecycle.allowed(header.getState(),
                LifecycleAction.CHECK_STATUS)) {
            return header;
        }

        AuthorityResponse response = engine.checkStatus(
                new com.einvoice.core.authority.StatusInput(
                        header.getCompanyId(),
                        header.getAuthorityEnvironmentId(),
                        TransactionType.INVOICE.name(),
                        header.getId(),
                        header.getEtaSubmissionId()));

        EtaInvoiceState resolved = resolveState(response);
        EtaInvoiceState beforeState = header.getState();
        EtaInvoiceState newState = resolved;
        if (newState != null && newState != beforeState) {
            LifecycleAction outcomeAction = mapOutcomeToAction(newState);
            if (!EtaInvoiceLifecycle.allowed(beforeState, outcomeAction)) {
                newState = beforeState;
            }
        } else {
            newState = beforeState;
        }

        UUID headerId = header.getId();
        final EtaInvoiceState targetState = newState;
        return txTemplate.execute(status -> {
            EtaInvoiceHeader h = headerRepository.findById(headerId)
                    .orElseThrow();
            if (targetState != beforeState) {
                h.setState(targetState);
                if (response.etaUuid() != null) {
                    h.setEtaUuid(response.etaUuid());
                }
                if (response.etaLongId() != null) {
                    h.setEtaLongId(response.etaLongId());
                }
                headerRepository.saveAndFlush(h);
            }
            auditService.record("CHECK_STATUS_INVOICE",
                    "ETA_INVOICE", headerId.toString(),
                    Map.of("state", beforeState.name()),
                    Map.of("state", targetState.name()));
            return h;
        });
    }

    private EtaInvoiceHeader doSubmit(EtaInvoiceHeader header,
            LifecycleAction action) {
        EtaInvoiceState targetState = EtaInvoiceLifecycle.next(
                header.getState(), action);

        UUID headerId = header.getId();
        UUID companyId = header.getCompanyId();
        Short authEnvId = header.getAuthorityEnvironmentId();

        final EtaAuthorityEngine.Preparation[] prepHolder =
                new EtaAuthorityEngine.Preparation[1];
        final UUID[] attemptIdHolder = new UUID[1];
        final int[] attemptNumberHolder = new int[1];

        txTemplate.executeWithoutResult(status -> {
            EtaInvoiceHeader h = headerRepository.findById(headerId)
                    .orElseThrow();
            h.setState(targetState);
            headerRepository.saveAndFlush(h);

            int next = getNextAttemptNumber(headerId);
            SubmissionAttempt attempt = SubmissionAttempt.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(authEnvId)
                    .transactionType(TransactionType.INVOICE)
                    .documentId(headerId)
                    .attemptNumber(next)
                    .submittedBy(TenantContext.getUserId())
                    .build();
            attempt = attemptRepository.save(attempt);
            attemptIdHolder[0] = attempt.getId();
            attemptNumberHolder[0] = next;

            prepHolder[0] = engine.prepareSubmission(h, companyId,
                    authEnvId);
        });

        EtaAuthorityEngine.Preparation prep = prepHolder[0];
        UUID attemptId = attemptIdHolder[0];
        String signedBase64 = Base64.getEncoder().encodeToString(
                prep.signedPayload().canonicalBytes());

        AuthorityResponse response;
        try {
            response = engine.httpSubmit(prep, companyId,
                    authEnvId, headerId);
        } catch (Exception e) {
            return handleHttpError(headerId, attemptId, signedBase64,
                    e.getMessage(), attemptNumberHolder[0]);
        }

        return finalizeSubmission(headerId, attemptId, signedBase64,
                response, targetState, attemptNumberHolder[0]);
    }

    private EtaInvoiceHeader handleHttpError(UUID headerId,
            UUID attemptId, String signedBase64, String errorMessage,
            int attemptNumber) {
        return txTemplate.execute(status -> {
            attemptRepository.finalizeAttempt(attemptId,
                    SubmissionResult.AMBIGUOUS, null, errorMessage, null,
                    OffsetDateTime.now());

            EtaInvoiceHeader h = headerRepository.findById(headerId)
                    .orElseThrow();
            h.setState(EtaInvoiceState.SUBMISSION_AMBIGUOUS);
            headerRepository.saveAndFlush(h);

            recordArtifactWithAttempt(h, ArtifactType.SIGNED_JSON,
                    signedBase64, attemptNumber);

            auditService.record("SUBMIT_INVOICE", "ETA_INVOICE",
                    headerId.toString(), null,
                    Map.of("state",
                            EtaInvoiceState.SUBMISSION_AMBIGUOUS.name(),
                            "error", String.valueOf(errorMessage)));
            return h;
        });
    }

    private EtaInvoiceHeader finalizeSubmission(UUID headerId,
            UUID attemptId, String signedBase64,
            AuthorityResponse response, EtaInvoiceState fromState,
            int attemptNumber) {
        return txTemplate.execute(status -> {
            SubmissionResult result = mapResult(response);
            attemptRepository.finalizeAttempt(attemptId, result,
                    response.statusCode(), response.errorSummary(),
                    null, OffsetDateTime.now());

            EtaInvoiceHeader h = headerRepository.findById(headerId)
                    .orElseThrow();

            recordArtifactWithAttempt(h, ArtifactType.SIGNED_JSON,
                    signedBase64, attemptNumber);

            if (response.rawResponse() != null) {
                recordArtifactWithAttempt(h, ArtifactType.ETA_RESPONSE,
                        response.rawResponse(), attemptNumber);
            }

            EtaInvoiceState outcomeState = resolveState(response);
            if (outcomeState != null) {
                LifecycleAction outcomeAction =
                        mapOutcomeToAction(outcomeState);
                if (!EtaInvoiceLifecycle.allowed(
                        fromState, outcomeAction)) {
                    throw new InvalidLifecycleTransitionException(
                            "Transition not allowed: " + fromState
                                    + " + " + outcomeAction,
                            fromState.name(),
                            outcomeAction.name());
                }
                h.setState(outcomeState);
            } else {
                h.setState(EtaInvoiceState.SUBMISSION_AMBIGUOUS);
            }

            h.setEtaUuid(response.etaUuid());
            h.setEtaLongId(response.etaLongId());
            h.setEtaSubmissionId(response.etaSubmissionId());

            headerRepository.saveAndFlush(h);
            auditService.record("SUBMIT_INVOICE", "ETA_INVOICE",
                    headerId.toString(), null,
                    Map.of("state", h.getState().name()));
            return h;
        });
    }

    private int getNextAttemptNumber(UUID documentId) {
        return attemptRepository.findMaxAttemptNumber(documentId)
                .map(max -> max + 1)
                .orElse(1);
    }

    private void recordArtifactWithAttempt(EtaInvoiceHeader header,
            ArtifactType type, String content, int attemptNumber) {
        String hash = sha256Hex(content);
        InvoiceArtifact artifact = InvoiceArtifact.builder()
                .companyId(header.getCompanyId())
                .authorityEnvironmentId(
                        header.getAuthorityEnvironmentId())
                .transactionType(TransactionType.INVOICE)
                .documentId(header.getId())
                .artifactType(type)
                .attemptNumber(attemptNumber)
                .content(content)
                .contentHash(hash)
                .build();
        artifactRepository.save(artifact);
    }

    private SubmissionResult mapResult(AuthorityResponse response) {
        if (response == null) {
            return SubmissionResult.AMBIGUOUS;
        }
        String r = response.result();
        if ("SUCCESS".equals(r)) {
            return SubmissionResult.SUCCESS;
        }
        if ("REJECTED".equals(r)) {
            return SubmissionResult.REJECTED;
        }
        if ("ERROR".equals(r)) {
            return SubmissionResult.ERROR;
        }
        if ("TIMEOUT".equals(r)) {
            return SubmissionResult.TIMEOUT;
        }
        return SubmissionResult.AMBIGUOUS;
    }

    private EtaInvoiceState resolveState(
            AuthorityResponse response) {
        if (response == null) {
            return null;
        }
        if (response.success()) {
            return EtaInvoiceState.VALID;
        }
        if ("REJECTED".equals(response.result())) {
            return EtaInvoiceState.REJECTED;
        }
        return null;
    }

    private LifecycleAction mapOutcomeToAction(
            EtaInvoiceState state) {
        if (state == EtaInvoiceState.VALID) {
            return LifecycleAction.MARK_VALID;
        }
        if (state == EtaInvoiceState.REJECTED) {
            return LifecycleAction.MARK_REJECTED;
        }
        if (state == EtaInvoiceState.IN_REVIEW) {
            return LifecycleAction.MARK_IN_REVIEW;
        }
        return LifecycleAction.MARK_AMBIGUOUS;
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to compute SHA-256", e);
        }
    }

    // --- Receipt submission methods ---

    /**
     * Submits a DRAFT receipt to the ETA authority.
     *
     * @param header the receipt header (must be DRAFT)
     * @return the updated header with final state
     */
    public EtaReceiptHeader submitReceipt(EtaReceiptHeader header) {
        return doSubmitReceipt(header, LifecycleAction.SUBMIT);
    }

    /**
     * Retries a previously ambiguous receipt submission.
     *
     * @param header the receipt header (must be SUBMISSION_AMBIGUOUS)
     * @return the updated header with final state
     */
    public EtaReceiptHeader retryReceipt(EtaReceiptHeader header) {
        return doSubmitReceipt(header, LifecycleAction.RETRY);
    }

    /**
     * Cancels a previously VALID receipt.
     *
     * @param header the receipt header (must be VALID)
     * @param reason the cancellation reason
     * @return the updated header (CANCELLED)
     */
    public EtaReceiptHeader cancelReceipt(EtaReceiptHeader header,
            String reason) {
        EtaReceiptState targetState = EtaReceiptLifecycle.next(
                header.getState(), LifecycleAction.CANCEL);
        UUID headerId = header.getId();
        UUID companyId = header.getCompanyId();
        Short authEnvId = header.getAuthorityEnvironmentId();

        final UUID[] attemptIdHolder = new UUID[1];
        final int[] attemptNumberHolder = new int[1];

        txTemplate.executeWithoutResult(status -> {
            int next = getNextAttemptNumber(headerId);
            SubmissionAttempt attempt = SubmissionAttempt.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(authEnvId)
                    .transactionType(TransactionType.RECEIPT)
                    .documentId(headerId)
                    .attemptNumber(next)
                    .submittedBy(TenantContext.getUserId())
                    .build();
            attempt = attemptRepository.save(attempt);
            attemptIdHolder[0] = attempt.getId();
            attemptNumberHolder[0] = next;
        });

        AuthorityResponse response = engine.cancel(
                new CancelInput(companyId, authEnvId,
                        TransactionType.RECEIPT.name(),
                        headerId, header.getEtaReceiptUuid(), reason));

        return txTemplate.execute(status -> {
            SubmissionResult result = mapResult(response);
            attemptRepository.finalizeAttempt(attemptIdHolder[0], result,
                    response.statusCode(), response.errorSummary(),
                    null, OffsetDateTime.now());

            EtaReceiptHeader h = receiptHeaderRepository.findById(headerId)
                    .orElseThrow();
            if (result == SubmissionResult.SUCCESS) {
                h.setState(targetState);
            }
            if (response.etaUuid() != null) {
                h.setEtaReceiptUuid(response.etaUuid());
            }

            if (response.rawResponse() != null) {
                recordReceiptArtifactWithAttempt(h, ArtifactType.ETA_RESPONSE,
                        response.rawResponse(), attemptNumberHolder[0]);
            }

            receiptHeaderRepository.saveAndFlush(h);
            auditService.record("CANCEL_RECEIPT", "ETA_RECEIPT",
                    headerId.toString(),
                    Map.of("reason", reason != null ? reason : ""),
                    Map.of("state", h.getState().name(),
                            "result", result.name()));
            return h;
        });
    }

    /**
     * Checks the status of a submitted receipt.
     *
     * @param header the receipt header
     * @return the updated header
     */
    public EtaReceiptHeader checkReceiptStatus(EtaReceiptHeader header) {
        if (!EtaReceiptLifecycle.allowed(header.getState(),
                LifecycleAction.CHECK_STATUS)) {
            return header;
        }

        AuthorityResponse response = engine.checkStatus(
                new com.einvoice.core.authority.StatusInput(
                        header.getCompanyId(),
                        header.getAuthorityEnvironmentId(),
                        TransactionType.RECEIPT.name(),
                        header.getId(),
                        header.getEtaSubmissionId()));

        EtaReceiptState resolved = resolveReceiptState(response);
        EtaReceiptState beforeState = header.getState();
        EtaReceiptState newState = resolved;
        if (newState != null && newState != beforeState) {
            LifecycleAction outcomeAction = mapReceiptOutcomeToAction(newState);
            if (!EtaReceiptLifecycle.allowed(beforeState, outcomeAction)) {
                newState = beforeState;
            }
        } else {
            newState = beforeState;
        }

        UUID headerId = header.getId();
        final EtaReceiptState targetState = newState;
        return txTemplate.execute(status -> {
            EtaReceiptHeader h = receiptHeaderRepository.findById(headerId)
                    .orElseThrow();
            if (targetState != beforeState) {
                h.setState(targetState);
                if (response.etaUuid() != null) {
                    h.setEtaReceiptUuid(response.etaUuid());
                }
                receiptHeaderRepository.saveAndFlush(h);
            }
            auditService.record("CHECK_STATUS_RECEIPT",
                    "ETA_RECEIPT", headerId.toString(),
                    Map.of("state", beforeState.name()),
                    Map.of("state", targetState.name()));
            return h;
        });
    }

    private EtaReceiptHeader doSubmitReceipt(EtaReceiptHeader header,
            LifecycleAction action) {
        EtaReceiptState targetState = EtaReceiptLifecycle.next(
                header.getState(), action);

        UUID headerId = header.getId();
        UUID companyId = header.getCompanyId();
        Short authEnvId = header.getAuthorityEnvironmentId();

        final EtaAuthorityEngine.Preparation[] prepHolder =
                new EtaAuthorityEngine.Preparation[1];
        final UUID[] attemptIdHolder = new UUID[1];
        final int[] attemptNumberHolder = new int[1];

        txTemplate.executeWithoutResult(status -> {
            EtaReceiptHeader h = receiptHeaderRepository.findById(headerId)
                    .orElseThrow();
            h.setState(targetState);
            receiptHeaderRepository.saveAndFlush(h);

            int next = getNextAttemptNumber(headerId);
            SubmissionAttempt attempt = SubmissionAttempt.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(authEnvId)
                    .transactionType(TransactionType.RECEIPT)
                    .documentId(headerId)
                    .attemptNumber(next)
                    .submittedBy(TenantContext.getUserId())
                    .build();
            attempt = attemptRepository.save(attempt);
            attemptIdHolder[0] = attempt.getId();
            attemptNumberHolder[0] = next;

            prepHolder[0] = engine.prepareReceiptSubmission(h, companyId,
                    authEnvId);
        });

        EtaAuthorityEngine.Preparation prep = prepHolder[0];
        UUID attemptId = attemptIdHolder[0];
        String signedBase64 = Base64.getEncoder().encodeToString(
                prep.signedPayload().canonicalBytes());

        AuthorityResponse response;
        try {
            response = engine.httpSubmitReceipt(prep, companyId,
                    authEnvId, headerId);
        } catch (Exception e) {
            return handleReceiptHttpError(headerId, attemptId,
                    signedBase64, e.getMessage(), attemptNumberHolder[0]);
        }

        return finalizeReceiptSubmission(headerId, attemptId, signedBase64,
                response, targetState, attemptNumberHolder[0]);
    }

    private EtaReceiptHeader handleReceiptHttpError(UUID headerId,
            UUID attemptId, String signedBase64, String errorMessage,
            int attemptNumber) {
        return txTemplate.execute(status -> {
            attemptRepository.finalizeAttempt(attemptId,
                    SubmissionResult.AMBIGUOUS, null, errorMessage, null,
                    OffsetDateTime.now());

            EtaReceiptHeader h = receiptHeaderRepository.findById(headerId)
                    .orElseThrow();
            h.setState(EtaReceiptState.SUBMISSION_AMBIGUOUS);
            receiptHeaderRepository.saveAndFlush(h);

            recordReceiptArtifactWithAttempt(h, ArtifactType.SIGNED_JSON,
                    signedBase64, attemptNumber);

            auditService.record("SUBMIT_RECEIPT", "ETA_RECEIPT",
                    headerId.toString(), null,
                    Map.of("state",
                            EtaReceiptState.SUBMISSION_AMBIGUOUS.name(),
                            "error", String.valueOf(errorMessage)));
            return h;
        });
    }

    private EtaReceiptHeader finalizeReceiptSubmission(UUID headerId,
            UUID attemptId, String signedBase64,
            AuthorityResponse response, EtaReceiptState fromState,
            int attemptNumber) {
        return txTemplate.execute(status -> {
            SubmissionResult result = mapResult(response);
            attemptRepository.finalizeAttempt(attemptId, result,
                    response.statusCode(), response.errorSummary(),
                    null, OffsetDateTime.now());

            EtaReceiptHeader h = receiptHeaderRepository.findById(headerId)
                    .orElseThrow();

            recordReceiptArtifactWithAttempt(h, ArtifactType.SIGNED_JSON,
                    signedBase64, attemptNumber);

            if (response.rawResponse() != null) {
                recordReceiptArtifactWithAttempt(h, ArtifactType.ETA_RESPONSE,
                        response.rawResponse(), attemptNumber);
            }

            EtaReceiptState outcomeState = resolveReceiptState(response);
            if (outcomeState != null) {
                LifecycleAction outcomeAction =
                        mapReceiptOutcomeToAction(outcomeState);
                if (!EtaReceiptLifecycle.allowed(
                        fromState, outcomeAction)) {
                    throw new InvalidLifecycleTransitionException(
                            "Transition not allowed: " + fromState
                                    + " + " + outcomeAction,
                            fromState.name(),
                            outcomeAction.name());
                }
                h.setState(outcomeState);
            } else {
                h.setState(EtaReceiptState.SUBMISSION_AMBIGUOUS);
            }

            h.setEtaReceiptUuid(response.etaUuid());
            h.setEtaSubmissionId(response.etaSubmissionId());

            receiptHeaderRepository.saveAndFlush(h);
            auditService.record("SUBMIT_RECEIPT", "ETA_RECEIPT",
                    headerId.toString(), null,
                    Map.of("state", h.getState().name()));
            return h;
        });
    }

    private void recordReceiptArtifactWithAttempt(EtaReceiptHeader header,
            ArtifactType type, String content, int attemptNumber) {
        String hash = sha256Hex(content);
        InvoiceArtifact artifact = InvoiceArtifact.builder()
                .companyId(header.getCompanyId())
                .authorityEnvironmentId(
                        header.getAuthorityEnvironmentId())
                .transactionType(TransactionType.RECEIPT)
                .documentId(header.getId())
                .artifactType(type)
                .attemptNumber(attemptNumber)
                .content(content)
                .contentHash(hash)
                .build();
        artifactRepository.save(artifact);
    }

    private EtaReceiptState resolveReceiptState(
            AuthorityResponse response) {
        if (response == null) {
            return null;
        }
        if (response.success()) {
            return EtaReceiptState.VALID;
        }
        if ("REJECTED".equals(response.result())) {
            return EtaReceiptState.REJECTED;
        }
        return null;
    }

    private LifecycleAction mapReceiptOutcomeToAction(
            EtaReceiptState state) {
        if (state == EtaReceiptState.VALID) {
            return LifecycleAction.MARK_VALID;
        }
        if (state == EtaReceiptState.REJECTED) {
            return LifecycleAction.MARK_REJECTED;
        }
        if (state == EtaReceiptState.IN_REVIEW) {
            return LifecycleAction.MARK_IN_REVIEW;
        }
        return LifecycleAction.MARK_AMBIGUOUS;
    }
}
