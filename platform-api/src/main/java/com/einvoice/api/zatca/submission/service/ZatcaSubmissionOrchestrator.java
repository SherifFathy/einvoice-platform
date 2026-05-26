package com.einvoice.api.zatca.submission.service;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.CancelInput;
import com.einvoice.core.domain.shared.ArtifactType;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.InvoiceArtifact;
import com.einvoice.core.domain.shared.LifecycleAction;
import com.einvoice.core.domain.shared.LifecycleTransitions;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.zatca.ZatcaChainState;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.repository.shared.InvoiceArtifactRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.core.repository.zatca.ZatcaSimplifiedHeaderRepository;
import com.einvoice.core.repository.zatca.ZatcaStandardHeaderRepository;
import com.einvoice.security.tenant.TenantContext;
import com.einvoice.zatca.chain.ZatcaChainService;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine.ZatcaSubmissionResult;
import com.einvoice.zatca.status.ZatcaStatusService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Orchestrates ZATCA document submission for Standard and Simplified flows. */
@Service
public class ZatcaSubmissionOrchestrator {

    /** Outcome wrapper containing the updated header and the submission attempt. */
    public record SubmissionOutcome<T>(
            T header,
            SubmissionAttempt attempt) {}

    private final ZatcaAuthorityEngine engine;
    private final ZatcaChainService chainService;
    private final ZatcaStatusService statusService;
    private final SubmissionAttemptRepository attemptRepository;
    private final InvoiceArtifactRepository artifactRepository;
    private final AuditService auditService;
    private final ZatcaStandardHeaderRepository standardHeaderRepository;
    private final ZatcaSimplifiedHeaderRepository simplifiedHeaderRepository;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    public ZatcaSubmissionOrchestrator(ZatcaAuthorityEngine engine,
            ZatcaChainService chainService,
            ZatcaStatusService statusService,
            SubmissionAttemptRepository attemptRepository,
            InvoiceArtifactRepository artifactRepository,
            AuditService auditService,
            ZatcaStandardHeaderRepository standardHeaderRepository,
            ZatcaSimplifiedHeaderRepository simplifiedHeaderRepository,
            TransactionTemplate txTemplate,
            ObjectMapper objectMapper) {
        this.engine = engine;
        this.chainService = chainService;
        this.statusService = statusService;
        this.attemptRepository = attemptRepository;
        this.artifactRepository = artifactRepository;
        this.auditService = auditService;
        this.standardHeaderRepository = standardHeaderRepository;
        this.simplifiedHeaderRepository = simplifiedHeaderRepository;
        this.txTemplate = txTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Submit a Standard (B2B) document to ZATCA clearance.
     *
     * @param header the standard document header
     * @return outcome with updated header and submission attempt
     */
    public SubmissionOutcome<ZatcaStandardHeader> submitStandard(
            ZatcaStandardHeader header) {
        LifecycleTransitions.assertAllowed(header.getStatus(),
                DocumentState.SUBMITTING, TransactionType.STANDARD);

        UUID headerId = header.getId();
        UUID companyId = header.getCompanyId();
        Short authEnvId = header.getAuthorityEnvironmentId();

        final UUID[] attemptIdHolder = new UUID[1];
        final int[] attemptNumberHolder = new int[1];
        final long[] chainCounterHolder = new long[1];
        final String[] previousHashHolder = new String[1];
        final ZatcaSubmissionResult[] resultHolder =
                new ZatcaSubmissionResult[1];

        txTemplate.executeWithoutResult(status -> {
            ZatcaStandardHeader h =
                    standardHeaderRepository.findById(headerId)
                            .orElseThrow();
            h.setStatus(DocumentState.SUBMITTING);
            standardHeaderRepository.saveAndFlush(h);

            ZatcaChainState chainRow =
                    chainService.acquireForUpdate(companyId, authEnvId);
            previousHashHolder[0] =
                    chainRow.getPreviousInvoiceHash();

            int next = getNextAttemptNumber(headerId);
            SubmissionAttempt attempt = SubmissionAttempt.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(authEnvId)
                    .transactionType(TransactionType.STANDARD)
                    .documentId(headerId)
                    .attemptNumber(next)
                    .submittedBy(TenantContext.getUserId())
                    .chainCounterSnapshot(chainRow.getInvoiceCounter())
                    .build();
            attempt = attemptRepository.save(attempt);
            attemptIdHolder[0] = attempt.getId();
            attemptNumberHolder[0] = next;

            ZatcaSubmissionResult result =
                    engine.prepareStandardSubmission(
                            h, companyId, authEnvId);
            resultHolder[0] = result;

            chainService.advance(chainRow, result.invoiceHash());
            chainCounterHolder[0] = chainRow.getInvoiceCounter();
        });

        AuthorityResponse response;
        try {
            response = engine.submitClearance(
                    resultHolder[0], companyId, authEnvId);
        } catch (Exception e) {
            return finalizeStandard(headerId, attemptIdHolder[0],
                    resultHolder[0], e.getMessage(),
                    attemptNumberHolder[0],
                    chainCounterHolder[0],
                    previousHashHolder[0]);
        }

        return finalizeStandard(headerId, attemptIdHolder[0],
                resultHolder[0], response, attemptNumberHolder[0],
                chainCounterHolder[0], previousHashHolder[0]);
    }

    /**
     * Submit a Simplified (B2C) document to ZATCA reporting.
     *
     * @param header the simplified document header
     * @return outcome with updated header and submission attempt
     */
    public SubmissionOutcome<ZatcaSimplifiedHeader> submitSimplified(
            ZatcaSimplifiedHeader header) {
        LifecycleTransitions.assertAllowed(header.getStatus(),
                DocumentState.SUBMITTING, TransactionType.SIMPLIFIED);

        UUID headerId = header.getId();
        UUID companyId = header.getCompanyId();
        Short authEnvId = header.getAuthorityEnvironmentId();

        final UUID[] attemptIdHolder = new UUID[1];
        final int[] attemptNumberHolder = new int[1];
        final long[] chainCounterHolder = new long[1];
        final String[] previousHashHolder = new String[1];
        final ZatcaSubmissionResult[] resultHolder =
                new ZatcaSubmissionResult[1];

        txTemplate.executeWithoutResult(status -> {
            ZatcaSimplifiedHeader h =
                    simplifiedHeaderRepository.findById(headerId)
                            .orElseThrow();
            h.setStatus(DocumentState.SUBMITTING);
            simplifiedHeaderRepository.saveAndFlush(h);

            ZatcaChainState chainRow =
                    chainService.acquireForUpdate(companyId, authEnvId);
            previousHashHolder[0] =
                    chainRow.getPreviousInvoiceHash();

            int next = getNextAttemptNumber(headerId);
            SubmissionAttempt attempt = SubmissionAttempt.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(authEnvId)
                    .transactionType(TransactionType.SIMPLIFIED)
                    .documentId(headerId)
                    .attemptNumber(next)
                    .submittedBy(TenantContext.getUserId())
                    .chainCounterSnapshot(chainRow.getInvoiceCounter())
                    .build();
            attempt = attemptRepository.save(attempt);
            attemptIdHolder[0] = attempt.getId();
            attemptNumberHolder[0] = next;

            ZatcaSubmissionResult result =
                    engine.prepareSimplifiedSubmission(
                            h, companyId, authEnvId);
            resultHolder[0] = result;

            chainService.advance(chainRow, result.invoiceHash());
            chainCounterHolder[0] = chainRow.getInvoiceCounter();
        });

        AuthorityResponse response;
        try {
            response = engine.submitReporting(
                    resultHolder[0], companyId, authEnvId);
        } catch (Exception e) {
            return finalizeSimplified(headerId, attemptIdHolder[0],
                    resultHolder[0], e.getMessage(),
                    attemptNumberHolder[0],
                    chainCounterHolder[0],
                    previousHashHolder[0]);
        }

        return finalizeSimplified(headerId, attemptIdHolder[0],
                resultHolder[0], response, attemptNumberHolder[0],
                chainCounterHolder[0], previousHashHolder[0]);
    }

    public SubmissionOutcome<ZatcaStandardHeader> cancelStandard(
            ZatcaStandardHeader header, String reason) {
        if (header.getZatcaUuid() == null
                || header.getZatcaUuid().isBlank()) {
            throw new IllegalStateException(
                    "Cannot cancel: document has no ZATCA UUID");
        }
        DocumentState targetState = LifecycleTransitions.next(
                header.getStatus(), LifecycleAction.CANCEL,
                TransactionType.STANDARD);

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
                    .transactionType(TransactionType.STANDARD)
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
                        TransactionType.STANDARD.name(),
                        headerId, header.getZatcaUuid(), reason));

        return txTemplate.execute(status -> {
            SubmissionResult result = mapResult(response);
            attemptRepository.finalizeAttempt(attemptIdHolder[0], result,
                    response.statusCode(), response.errorSummary(),
                    response.rawResponse(), OffsetDateTime.now());

            ZatcaStandardHeader h =
                    standardHeaderRepository.findById(headerId)
                            .orElseThrow();
            if (result == SubmissionResult.SUCCESS) {
                h.setStatus(targetState);
            }
            if (response.rawResponse() != null) {
                recordArtifact(h, ArtifactType.ZATCA_RESPONSE,
                        response.rawResponse(),
                        attemptNumberHolder[0],
                        TransactionType.STANDARD);
            }

            standardHeaderRepository.saveAndFlush(h);
            auditService.record("CANCEL_STANDARD", "ZATCA_STANDARD",
                    headerId.toString(),
                    Map.of("reason", reason != null ? reason : ""),
                    Map.of("status", h.getStatus().name(),
                            "result", result.name()));
            SubmissionAttempt attempt =
                    attemptRepository.findById(attemptIdHolder[0])
                            .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    public SubmissionOutcome<ZatcaSimplifiedHeader> cancelSimplified(
            ZatcaSimplifiedHeader header, String reason) {
        if (header.getZatcaUuid() == null
                || header.getZatcaUuid().isBlank()) {
            throw new IllegalStateException(
                    "Cannot cancel: document has no ZATCA UUID");
        }
        DocumentState targetState = LifecycleTransitions.next(
                header.getStatus(), LifecycleAction.CANCEL,
                TransactionType.SIMPLIFIED);

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
                    .transactionType(TransactionType.SIMPLIFIED)
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
                        TransactionType.SIMPLIFIED.name(),
                        headerId, header.getZatcaUuid(), reason));

        return txTemplate.execute(status -> {
            SubmissionResult result = mapResult(response);
            attemptRepository.finalizeAttempt(attemptIdHolder[0], result,
                    response.statusCode(), response.errorSummary(),
                    response.rawResponse(), OffsetDateTime.now());

            ZatcaSimplifiedHeader h =
                    simplifiedHeaderRepository.findById(headerId)
                            .orElseThrow();
            if (result == SubmissionResult.SUCCESS) {
                h.setStatus(targetState);
            }
            if (response.rawResponse() != null) {
                recordArtifact(h, ArtifactType.ZATCA_RESPONSE,
                        response.rawResponse(),
                        attemptNumberHolder[0],
                        TransactionType.SIMPLIFIED);
            }

            simplifiedHeaderRepository.saveAndFlush(h);
            auditService.record("CANCEL_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", headerId.toString(),
                    Map.of("reason", reason != null ? reason : ""),
                    Map.of("status", h.getStatus().name(),
                            "result", result.name()));
            SubmissionAttempt attempt =
                    attemptRepository.findById(attemptIdHolder[0])
                            .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    public SubmissionOutcome<ZatcaStandardHeader> retryStandard(
            ZatcaStandardHeader header) {
        if (header.getStatus() != DocumentState.IN_REVIEW) {
            throw new com.einvoice.core.error
                    .InvalidLifecycleTransitionException(
                    "Retry is only allowed from IN_REVIEW, "
                            + "current state: "
                            + header.getStatus(),
                    header.getStatus().name(), "RETRY");
        }

        UUID headerId = header.getId();
        UUID companyId = header.getCompanyId();
        Short authEnvId = header.getAuthorityEnvironmentId();

        final UUID[] attemptIdHolder = new UUID[1];
        final int[] attemptNumberHolder = new int[1];
        final ZatcaSubmissionResult[] resultHolder =
                new ZatcaSubmissionResult[1];

        txTemplate.executeWithoutResult(status -> {
            ZatcaStandardHeader h =
                    standardHeaderRepository.findById(headerId)
                            .orElseThrow();
            h.setStatus(DocumentState.SUBMITTING);
            standardHeaderRepository.saveAndFlush(h);

            int next = getNextAttemptNumber(headerId);
            SubmissionAttempt attempt = SubmissionAttempt.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(authEnvId)
                    .transactionType(TransactionType.STANDARD)
                    .documentId(headerId)
                    .attemptNumber(next)
                    .submittedBy(TenantContext.getUserId())
                    .build();
            attempt = attemptRepository.save(attempt);
            attemptIdHolder[0] = attempt.getId();
            attemptNumberHolder[0] = next;

            ZatcaSubmissionResult result =
                    engine.prepareStandardSubmission(
                            h, companyId, authEnvId);
            resultHolder[0] = result;
        });

        AuthorityResponse response;
        try {
            response = engine.submitClearance(
                    resultHolder[0], companyId, authEnvId);
        } catch (Exception e) {
            return finalizeRetryStandard(headerId, attemptIdHolder[0],
                    resultHolder[0], e.getMessage(),
                    attemptNumberHolder[0]);
        }

        return finalizeRetryStandard(headerId, attemptIdHolder[0],
                resultHolder[0], response, attemptNumberHolder[0]);
    }

    public SubmissionOutcome<ZatcaSimplifiedHeader> retrySimplified(
            ZatcaSimplifiedHeader header) {
        if (header.getStatus() != DocumentState.IN_REVIEW) {
            throw new com.einvoice.core.error
                    .InvalidLifecycleTransitionException(
                    "Retry is only allowed from IN_REVIEW, "
                            + "current state: "
                            + header.getStatus(),
                    header.getStatus().name(), "RETRY");
        }

        UUID headerId = header.getId();
        UUID companyId = header.getCompanyId();
        Short authEnvId = header.getAuthorityEnvironmentId();

        final UUID[] attemptIdHolder = new UUID[1];
        final int[] attemptNumberHolder = new int[1];
        final ZatcaSubmissionResult[] resultHolder =
                new ZatcaSubmissionResult[1];

        txTemplate.executeWithoutResult(status -> {
            ZatcaSimplifiedHeader h =
                    simplifiedHeaderRepository.findById(headerId)
                            .orElseThrow();
            h.setStatus(DocumentState.SUBMITTING);
            simplifiedHeaderRepository.saveAndFlush(h);

            int next = getNextAttemptNumber(headerId);
            SubmissionAttempt attempt = SubmissionAttempt.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(authEnvId)
                    .transactionType(TransactionType.SIMPLIFIED)
                    .documentId(headerId)
                    .attemptNumber(next)
                    .submittedBy(TenantContext.getUserId())
                    .build();
            attempt = attemptRepository.save(attempt);
            attemptIdHolder[0] = attempt.getId();
            attemptNumberHolder[0] = next;

            ZatcaSubmissionResult result =
                    engine.prepareSimplifiedSubmission(
                            h, companyId, authEnvId);
            resultHolder[0] = result;
        });

        AuthorityResponse response;
        try {
            response = engine.submitReporting(
                    resultHolder[0], companyId, authEnvId);
        } catch (Exception e) {
            return finalizeRetrySimplified(headerId, attemptIdHolder[0],
                    resultHolder[0], e.getMessage(),
                    attemptNumberHolder[0]);
        }

        return finalizeRetrySimplified(headerId, attemptIdHolder[0],
                resultHolder[0], response, attemptNumberHolder[0]);
    }

    public SubmissionOutcome<ZatcaStandardHeader> checkStandardStatus(
            ZatcaStandardHeader header) {
        if (!LifecycleTransitions.allowed(header.getStatus(),
                LifecycleAction.CHECK_STATUS,
                TransactionType.STANDARD)) {
            return new SubmissionOutcome<>(header, null);
        }

        AuthorityResponse response = statusService.checkStatus(
                header.getCompanyId(),
                header.getAuthorityEnvironmentId(),
                TransactionType.STANDARD, header.getId(),
                header.getZatcaUuid());

        DocumentState beforeState = header.getStatus();
        DocumentState newState =
                determineOutcomeState(true, response,
                        TransactionType.STANDARD);

        if (newState == beforeState) {
            auditService.record("CHECK_STATUS_STANDARD",
                    "ZATCA_STANDARD", header.getId().toString(),
                    Map.of("status", beforeState.name()),
                    Map.of("status", beforeState.name(),
                            "authorityResult",
                            response.result() != null
                                    ? response.result() : ""));
            return new SubmissionOutcome<>(header, null);
        }

        LifecycleAction outcomeAction =
                mapOutcomeToAction(newState);
        if (!LifecycleTransitions.allowed(beforeState, outcomeAction,
                TransactionType.STANDARD)) {
            auditService.record("CHECK_STATUS_STANDARD",
                    "ZATCA_STANDARD", header.getId().toString(),
                    Map.of("status", beforeState.name()),
                    Map.of("status", beforeState.name(),
                            "authorityResult",
                            response.result() != null
                                    ? response.result() : "",
                            "transitionBlocked", "true"));
            return new SubmissionOutcome<>(header, null);
        }

        UUID headerId = header.getId();
        final DocumentState targetState = newState;
        return txTemplate.execute(status -> {
            ZatcaStandardHeader h =
                    standardHeaderRepository.findById(headerId)
                            .orElseThrow();
            h.setStatus(targetState);
            if (response.result() != null) {
                h.setClearanceStatus(response.result());
            }
            if (response.rawResponse() != null) {
                h.setZatcaResponseData(parseResponseToMap(
                        response.rawResponse()));
            }
            standardHeaderRepository.saveAndFlush(h);
            auditService.record("CHECK_STATUS_STANDARD",
                    "ZATCA_STANDARD", headerId.toString(),
                    Map.of("status", beforeState.name()),
                    Map.of("status", targetState.name()));
            return new SubmissionOutcome<>(h, null);
        });
    }

    public SubmissionOutcome<ZatcaSimplifiedHeader>
            checkSimplifiedStatus(ZatcaSimplifiedHeader header) {
        if (!LifecycleTransitions.allowed(header.getStatus(),
                LifecycleAction.CHECK_STATUS,
                TransactionType.SIMPLIFIED)) {
            return new SubmissionOutcome<>(header, null);
        }

        AuthorityResponse response = statusService.checkStatus(
                header.getCompanyId(),
                header.getAuthorityEnvironmentId(),
                TransactionType.SIMPLIFIED, header.getId(),
                header.getZatcaUuid());

        DocumentState beforeState = header.getStatus();
        DocumentState newState =
                determineOutcomeState(true, response,
                        TransactionType.SIMPLIFIED);

        if (newState == beforeState) {
            auditService.record("CHECK_STATUS_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", header.getId().toString(),
                    Map.of("status", beforeState.name()),
                    Map.of("status", beforeState.name(),
                            "authorityResult",
                            response.result() != null
                                    ? response.result() : ""));
            return new SubmissionOutcome<>(header, null);
        }

        LifecycleAction outcomeAction =
                mapOutcomeToAction(newState);
        if (!LifecycleTransitions.allowed(beforeState, outcomeAction,
                TransactionType.SIMPLIFIED)) {
            auditService.record("CHECK_STATUS_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", header.getId().toString(),
                    Map.of("status", beforeState.name()),
                    Map.of("status", beforeState.name(),
                            "authorityResult",
                            response.result() != null
                                    ? response.result() : "",
                            "transitionBlocked", "true"));
            return new SubmissionOutcome<>(header, null);
        }

        UUID headerId = header.getId();
        final DocumentState targetState = newState;
        return txTemplate.execute(status -> {
            ZatcaSimplifiedHeader h =
                    simplifiedHeaderRepository.findById(headerId)
                            .orElseThrow();
            h.setStatus(targetState);
            if (response.result() != null) {
                h.setReportingStatus(response.result());
            }
            if (response.rawResponse() != null) {
                h.setZatcaResponseData(parseResponseToMap(
                        response.rawResponse()));
            }
            simplifiedHeaderRepository.saveAndFlush(h);
            auditService.record("CHECK_STATUS_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", headerId.toString(),
                    Map.of("status", beforeState.name()),
                    Map.of("status", targetState.name()));
            return new SubmissionOutcome<>(h, null);
        });
    }

    private SubmissionOutcome<ZatcaStandardHeader> finalizeStandard(UUID headerId,
            UUID attemptId, ZatcaSubmissionResult result,
            AuthorityResponse response, int attemptNumber,
            long chainCounter, String previousChainHash) {
        return txTemplate.execute(status -> {
            boolean success = response != null
                    && response.success();
            SubmissionResult outcome = success
                    ? SubmissionResult.SUCCESS : SubmissionResult.ERROR;
            int httpStatus = response != null
                    && response.statusCode() != null
                    ? response.statusCode() : 500;
            String errorSummary = response != null
                    ? response.errorSummary() : null;
            String rawResponse = response != null
                    ? response.rawResponse() : null;

            attemptRepository.finalizeAttempt(attemptId, outcome,
                    httpStatus, errorSummary, rawResponse,
                    OffsetDateTime.now());

            ZatcaStandardHeader h =
                    standardHeaderRepository.findById(headerId)
                            .orElseThrow();

            DocumentState submittedState = LifecycleTransitions.next(
                    h.getStatus(), LifecycleAction.MARK_SUBMITTED,
                    TransactionType.STANDARD);
            h.setStatus(submittedState);

            h.setInvoiceCounterValue(chainCounter);
            h.setPreviousInvoiceHash(previousChainHash);

            if (result != null) {
                h.setInvoiceHash(result.invoiceHash());
                h.setQrCodeBase64(result.qrBase64());

                if (result.signedUblXml() != null) {
                    recordArtifact(h, ArtifactType.SIGNED_UBL_XML,
                            java.util.Base64.getEncoder()
                                    .encodeToString(
                                            result.signedUblXml()),
                            attemptNumber, TransactionType.STANDARD);
                }
                if (result.qrPng() != null
                        && result.qrPng().length > 0) {
                    recordArtifact(h, ArtifactType.QR_PNG,
                            java.util.Base64.getEncoder()
                                    .encodeToString(result.qrPng()),
                            attemptNumber, TransactionType.STANDARD);
                }
                if (result.ublXml() != null) {
                    recordArtifact(h, ArtifactType.UBL_XML,
                            java.util.Base64.getEncoder()
                                    .encodeToString(result.ublXml()),
                            attemptNumber, TransactionType.STANDARD);
                }
            }

            if (response != null) {
                recordArtifact(h, ArtifactType.ZATCA_RESPONSE,
                        rawResponse, attemptNumber,
                        TransactionType.STANDARD);
                if (response.etaUuid() != null) {
                    h.setZatcaUuid(response.etaUuid());
                } else if (response.etaSubmissionId() != null) {
                    h.setZatcaUuid(response.etaSubmissionId());
                }
                if (response.result() != null) {
                    h.setClearanceStatus(response.result());
                }
            }

            DocumentState outcomeState = determineOutcomeState(
                    success, response, TransactionType.STANDARD);
            LifecycleTransitions.assertAllowed(h.getStatus(),
                    outcomeState, TransactionType.STANDARD);
            h.setStatus(outcomeState);

            standardHeaderRepository.saveAndFlush(h);
            auditService.record("SUBMIT_STANDARD", "ZATCA_STANDARD",
                    headerId.toString(), null,
                    Map.of("status", h.getStatus().name()));
            SubmissionAttempt attempt = attemptRepository.findById(attemptId)
                    .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    private SubmissionOutcome<ZatcaStandardHeader> finalizeStandard(UUID headerId,
            UUID attemptId, ZatcaSubmissionResult result,
            String errorMessage, int attemptNumber,
            long chainCounter, String previousChainHash) {
        return txTemplate.execute(status -> {
            attemptRepository.finalizeAttempt(attemptId,
                    SubmissionResult.ERROR, 500, errorMessage, null,
                    OffsetDateTime.now());

            ZatcaStandardHeader h =
                    standardHeaderRepository.findById(headerId)
                            .orElseThrow();

            h.setInvoiceCounterValue(chainCounter);
            h.setPreviousInvoiceHash(previousChainHash);

            if (result != null) {
                h.setInvoiceHash(result.invoiceHash());
                h.setQrCodeBase64(result.qrBase64());

                if (result.signedUblXml() != null) {
                    recordArtifact(h, ArtifactType.SIGNED_UBL_XML,
                            java.util.Base64.getEncoder()
                                    .encodeToString(
                                            result.signedUblXml()),
                            attemptNumber, TransactionType.STANDARD);
                }
                if (result.qrPng() != null
                        && result.qrPng().length > 0) {
                    recordArtifact(h, ArtifactType.QR_PNG,
                            java.util.Base64.getEncoder()
                                    .encodeToString(result.qrPng()),
                            attemptNumber, TransactionType.STANDARD);
                }
                if (result.ublXml() != null) {
                    recordArtifact(h, ArtifactType.UBL_XML,
                            java.util.Base64.getEncoder()
                                    .encodeToString(result.ublXml()),
                            attemptNumber, TransactionType.STANDARD);
                }
            }

            DocumentState outcomeState = LifecycleTransitions.next(
                    h.getStatus(), LifecycleAction.MARK_IN_REVIEW,
                    TransactionType.STANDARD);
            h.setStatus(outcomeState);

            standardHeaderRepository.saveAndFlush(h);
            auditService.record("SUBMIT_STANDARD_AMBIGUOUS",
                    "ZATCA_STANDARD", headerId.toString(), null,
                    Map.of("status", h.getStatus().name(),
                            "error", errorMessage));
            SubmissionAttempt attempt = attemptRepository.findById(attemptId)
                    .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    private SubmissionOutcome<ZatcaSimplifiedHeader> finalizeSimplified(UUID headerId,
            UUID attemptId, ZatcaSubmissionResult result,
            AuthorityResponse response, int attemptNumber,
            long chainCounter, String previousChainHash) {
        return txTemplate.execute(status -> {
            boolean success = response != null
                    && response.success();
            SubmissionResult outcome = success
                    ? SubmissionResult.SUCCESS : SubmissionResult.ERROR;
            int httpStatus = response != null
                    && response.statusCode() != null
                    ? response.statusCode() : 500;
            String errorSummary = response != null
                    ? response.errorSummary() : null;
            String rawResponse = response != null
                    ? response.rawResponse() : null;

            attemptRepository.finalizeAttempt(attemptId, outcome,
                    httpStatus, errorSummary, rawResponse,
                    OffsetDateTime.now());

            ZatcaSimplifiedHeader h =
                    simplifiedHeaderRepository.findById(headerId)
                            .orElseThrow();

            DocumentState submittedState = LifecycleTransitions.next(
                    h.getStatus(), LifecycleAction.MARK_SUBMITTED,
                    TransactionType.SIMPLIFIED);
            h.setStatus(submittedState);

            h.setInvoiceCounterValue(chainCounter);
            h.setPreviousInvoiceHash(previousChainHash);

            if (result != null) {
                h.setInvoiceHash(result.invoiceHash());
                h.setQrCodeBase64(result.qrBase64());

                if (result.signedUblXml() != null) {
                    recordArtifact(h, ArtifactType.SIGNED_UBL_XML,
                            java.util.Base64.getEncoder()
                                    .encodeToString(
                                            result.signedUblXml()),
                            attemptNumber,
                            TransactionType.SIMPLIFIED);
                }
                if (result.qrPng() != null
                        && result.qrPng().length > 0) {
                    recordArtifact(h, ArtifactType.QR_PNG,
                            java.util.Base64.getEncoder()
                                    .encodeToString(result.qrPng()),
                            attemptNumber,
                            TransactionType.SIMPLIFIED);
                }
                if (result.ublXml() != null) {
                    recordArtifact(h, ArtifactType.UBL_XML,
                            java.util.Base64.getEncoder()
                                    .encodeToString(result.ublXml()),
                            attemptNumber,
                            TransactionType.SIMPLIFIED);
                }
            }

            if (response != null) {
                recordArtifact(h, ArtifactType.ZATCA_RESPONSE,
                        rawResponse, attemptNumber,
                        TransactionType.SIMPLIFIED);
                if (response.etaUuid() != null) {
                    h.setZatcaUuid(response.etaUuid());
                } else if (response.etaSubmissionId() != null) {
                    h.setZatcaUuid(response.etaSubmissionId());
                }
                if (response.result() != null) {
                    h.setReportingStatus(response.result());
                }
            }

            DocumentState outcomeState = determineOutcomeState(
                    success, response, TransactionType.SIMPLIFIED);
            LifecycleTransitions.assertAllowed(h.getStatus(),
                    outcomeState, TransactionType.SIMPLIFIED);
            h.setStatus(outcomeState);

            simplifiedHeaderRepository.saveAndFlush(h);
            auditService.record("SUBMIT_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", headerId.toString(), null,
                    Map.of("status", h.getStatus().name()));
            SubmissionAttempt attempt = attemptRepository.findById(attemptId)
                    .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    private SubmissionOutcome<ZatcaSimplifiedHeader> finalizeSimplified(UUID headerId,
            UUID attemptId, ZatcaSubmissionResult result,
            String errorMessage, int attemptNumber,
            long chainCounter, String previousChainHash) {
        return txTemplate.execute(status -> {
            attemptRepository.finalizeAttempt(attemptId,
                    SubmissionResult.ERROR, 500, errorMessage, null,
                    OffsetDateTime.now());

            ZatcaSimplifiedHeader h =
                    simplifiedHeaderRepository.findById(headerId)
                            .orElseThrow();

            h.setInvoiceCounterValue(chainCounter);
            h.setPreviousInvoiceHash(previousChainHash);

            if (result != null) {
                h.setInvoiceHash(result.invoiceHash());
                h.setQrCodeBase64(result.qrBase64());

                if (result.signedUblXml() != null) {
                    recordArtifact(h, ArtifactType.SIGNED_UBL_XML,
                            java.util.Base64.getEncoder()
                                    .encodeToString(
                                            result.signedUblXml()),
                            attemptNumber,
                            TransactionType.SIMPLIFIED);
                }
                if (result.qrPng() != null
                        && result.qrPng().length > 0) {
                    recordArtifact(h, ArtifactType.QR_PNG,
                            java.util.Base64.getEncoder()
                                    .encodeToString(result.qrPng()),
                            attemptNumber,
                            TransactionType.SIMPLIFIED);
                }
                if (result.ublXml() != null) {
                    recordArtifact(h, ArtifactType.UBL_XML,
                            java.util.Base64.getEncoder()
                                    .encodeToString(result.ublXml()),
                            attemptNumber,
                            TransactionType.SIMPLIFIED);
                }
            }

            DocumentState outcomeState = LifecycleTransitions.next(
                    h.getStatus(), LifecycleAction.MARK_IN_REVIEW,
                    TransactionType.SIMPLIFIED);
            h.setStatus(outcomeState);

            simplifiedHeaderRepository.saveAndFlush(h);
            auditService.record("SUBMIT_SIMPLIFIED_AMBIGUOUS",
                    "ZATCA_SIMPLIFIED", headerId.toString(), null,
                    Map.of("status", h.getStatus().name(),
                            "error", errorMessage));
            SubmissionAttempt attempt = attemptRepository.findById(attemptId)
                    .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    private DocumentState determineOutcomeState(boolean success,
            AuthorityResponse response, TransactionType txType) {
        if (!success) {
            return DocumentState.REJECTED;
        }
        if (response == null || response.result() == null) {
            return DocumentState.ACCEPTED;
        }
        String result = response.result();
        if ("CLEARED".equals(result)
                || "REPORTED".equals(result)
                || "CLEARED_WITH_WARNINGS".equals(result)
                || "REPORTED_WITH_WARNINGS".equals(result)) {
            return DocumentState.ACCEPTED;
        }
        if ("IN_REVIEW".equals(result)
                || "PENDING".equals(result)) {
            return DocumentState.IN_REVIEW;
        }
        if ("NOT_CLEARED".equals(result)
                || "NOT_REPORTED".equals(result)) {
            return DocumentState.REJECTED;
        }
        return DocumentState.ACCEPTED;
    }

    private SubmissionResult mapResult(AuthorityResponse response) {
        if (response == null || !response.success()) {
            return SubmissionResult.ERROR;
        }
        return SubmissionResult.SUCCESS;
    }

    private LifecycleAction mapOutcomeToAction(DocumentState state) {
        return switch (state) {
            case ACCEPTED -> LifecycleAction.MARK_ACCEPTED;
            case REJECTED -> LifecycleAction.MARK_REJECTED;
            case IN_REVIEW -> LifecycleAction.MARK_IN_REVIEW;
            case CANCELLED -> LifecycleAction.CANCEL;
            default -> LifecycleAction.CHECK_STATUS;
        };
    }

    private SubmissionOutcome<ZatcaStandardHeader> finalizeRetryStandard(
            UUID headerId, UUID attemptId,
            ZatcaSubmissionResult result,
            AuthorityResponse response, int attemptNumber) {
        return txTemplate.execute(status -> {
            boolean success = response != null
                    && response.success();
            SubmissionResult outcome = success
                    ? SubmissionResult.SUCCESS
                    : SubmissionResult.ERROR;
            int httpStatus = response != null
                    && response.statusCode() != null
                    ? response.statusCode() : 500;
            String errorSummary = response != null
                    ? response.errorSummary() : null;
            String rawResponse = response != null
                    ? response.rawResponse() : null;

            attemptRepository.finalizeAttempt(attemptId, outcome,
                    httpStatus, errorSummary, rawResponse,
                    OffsetDateTime.now());

            ZatcaStandardHeader h =
                    standardHeaderRepository.findById(headerId)
                            .orElseThrow();

            DocumentState submittedState = LifecycleTransitions.next(
                    h.getStatus(), LifecycleAction.MARK_SUBMITTED,
                    TransactionType.STANDARD);
            h.setStatus(submittedState);

            DocumentState outcomeState = determineOutcomeState(
                    success, response, TransactionType.STANDARD);
            LifecycleTransitions.assertAllowed(h.getStatus(),
                    outcomeState, TransactionType.STANDARD);
            h.setStatus(outcomeState);

            if (response != null) {
                recordArtifact(h, ArtifactType.ZATCA_RESPONSE,
                        rawResponse, attemptNumber,
                        TransactionType.STANDARD);
                if (response.result() != null) {
                    h.setClearanceStatus(response.result());
                }
            }

            standardHeaderRepository.saveAndFlush(h);
            auditService.record("RETRY_STANDARD", "ZATCA_STANDARD",
                    headerId.toString(), null,
                    Map.of("status", h.getStatus().name()));
            SubmissionAttempt attempt =
                    attemptRepository.findById(attemptId)
                            .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    private SubmissionOutcome<ZatcaStandardHeader> finalizeRetryStandard(
            UUID headerId, UUID attemptId,
            ZatcaSubmissionResult result,
            String errorMessage, int attemptNumber) {
        return txTemplate.execute(status -> {
            attemptRepository.finalizeAttempt(attemptId,
                    SubmissionResult.ERROR, 500, errorMessage, null,
                    OffsetDateTime.now());

            ZatcaStandardHeader h =
                    standardHeaderRepository.findById(headerId)
                            .orElseThrow();

            DocumentState outcomeState = DocumentState.IN_REVIEW;
            LifecycleTransitions.assertAllowed(h.getStatus(),
                    outcomeState, TransactionType.STANDARD);
            h.setStatus(outcomeState);

            standardHeaderRepository.saveAndFlush(h);
            auditService.record("RETRY_STANDARD_FAILED",
                    "ZATCA_STANDARD", headerId.toString(), null,
                    Map.of("status", h.getStatus().name(),
                            "error", errorMessage));
            SubmissionAttempt attempt =
                    attemptRepository.findById(attemptId)
                            .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    private SubmissionOutcome<ZatcaSimplifiedHeader>
            finalizeRetrySimplified(UUID headerId, UUID attemptId,
                    ZatcaSubmissionResult result,
                    AuthorityResponse response,
                    int attemptNumber) {
        return txTemplate.execute(status -> {
            boolean success = response != null
                    && response.success();
            SubmissionResult outcome = success
                    ? SubmissionResult.SUCCESS
                    : SubmissionResult.ERROR;
            int httpStatus = response != null
                    && response.statusCode() != null
                    ? response.statusCode() : 500;
            String errorSummary = response != null
                    ? response.errorSummary() : null;
            String rawResponse = response != null
                    ? response.rawResponse() : null;

            attemptRepository.finalizeAttempt(attemptId, outcome,
                    httpStatus, errorSummary, rawResponse,
                    OffsetDateTime.now());

            ZatcaSimplifiedHeader h =
                    simplifiedHeaderRepository.findById(headerId)
                            .orElseThrow();

            DocumentState submittedState = LifecycleTransitions.next(
                    h.getStatus(), LifecycleAction.MARK_SUBMITTED,
                    TransactionType.SIMPLIFIED);
            h.setStatus(submittedState);

            DocumentState outcomeState = determineOutcomeState(
                    success, response, TransactionType.SIMPLIFIED);
            LifecycleTransitions.assertAllowed(h.getStatus(),
                    outcomeState, TransactionType.SIMPLIFIED);
            h.setStatus(outcomeState);

            if (response != null) {
                recordArtifact(h, ArtifactType.ZATCA_RESPONSE,
                        rawResponse, attemptNumber,
                        TransactionType.SIMPLIFIED);
                if (response.result() != null) {
                    h.setReportingStatus(response.result());
                }
            }

            simplifiedHeaderRepository.saveAndFlush(h);
            auditService.record("RETRY_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", headerId.toString(), null,
                    Map.of("status", h.getStatus().name()));
            SubmissionAttempt attempt =
                    attemptRepository.findById(attemptId)
                            .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    private SubmissionOutcome<ZatcaSimplifiedHeader>
            finalizeRetrySimplified(UUID headerId, UUID attemptId,
                    ZatcaSubmissionResult result,
                    String errorMessage, int attemptNumber) {
        return txTemplate.execute(status -> {
            attemptRepository.finalizeAttempt(attemptId,
                    SubmissionResult.ERROR, 500, errorMessage, null,
                    OffsetDateTime.now());

            ZatcaSimplifiedHeader h =
                    simplifiedHeaderRepository.findById(headerId)
                            .orElseThrow();

            DocumentState outcomeState = DocumentState.IN_REVIEW;
            LifecycleTransitions.assertAllowed(h.getStatus(),
                    outcomeState, TransactionType.SIMPLIFIED);
            h.setStatus(outcomeState);

            simplifiedHeaderRepository.saveAndFlush(h);
            auditService.record("RETRY_SIMPLIFIED_FAILED",
                    "ZATCA_SIMPLIFIED", headerId.toString(), null,
                    Map.of("status", h.getStatus().name(),
                            "error", errorMessage));
            SubmissionAttempt attempt =
                    attemptRepository.findById(attemptId)
                            .orElse(null);
            return new SubmissionOutcome<>(h, attempt);
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponseToMap(
            String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawResponse,
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", rawResponse);
        }
    }

    private int getNextAttemptNumber(UUID documentId) {
        return attemptRepository.findMaxAttemptNumber(documentId)
                .map(max -> max + 1)
                .orElse(1);
    }

    private void recordArtifact(Object header, ArtifactType type,
            String content, int attemptNumber,
            TransactionType txType) {
        String hash = sha256Hex(content);
        UUID companyId;
        Short authEnvId;
        UUID docId;
        if (header instanceof ZatcaStandardHeader h) {
            companyId = h.getCompanyId();
            authEnvId = h.getAuthorityEnvironmentId();
            docId = h.getId();
        } else {
            ZatcaSimplifiedHeader h =
                    (ZatcaSimplifiedHeader) header;
            companyId = h.getCompanyId();
            authEnvId = h.getAuthorityEnvironmentId();
            docId = h.getId();
        }
        InvoiceArtifact artifact = InvoiceArtifact.builder()
                .companyId(companyId)
                .authorityEnvironmentId(authEnvId)
                .transactionType(txType)
                .documentId(docId)
                .artifactType(type)
                .attemptNumber(attemptNumber)
                .content(content)
                .contentHash(hash)
                .build();
        artifactRepository.save(artifact);
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
}
