package com.einvoice.core.service.submission;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable read-model records returned by {@link SubmissionLogQueryService} for
 * the unified submission log. These live in {@code platform-core} so the query
 * service can return them without depending on {@code platform-api}; the
 * submission-log controller maps each record to its wire DTO.
 */
public final class SubmissionLogReadModels {

    private SubmissionLogReadModels() {
    }

    /**
     * One row in the unified submission log.
     *
     * @param attemptId submission attempt identifier
     * @param companyId owning company
     * @param companyName owning company display name
     * @param transactionType {@code INVOICE/RECEIPT/STANDARD/SIMPLIFIED}
     * @param documentId the transmitted document
     * @param attemptNumber 1-based attempt sequence for the document
     * @param outcome {@code SUCCESS/REJECTED/ERROR/TIMEOUT/AMBIGUOUS/IN_FLIGHT}
     * @param statusCode authority HTTP-like status code, or {@code null}
     * @param errorSummary authority error summary, or {@code null}
     * @param submittedAt UTC submission timestamp
     * @param completedAt UTC completion timestamp, or {@code null} in flight
     * @param submittedBy submitting user id, or {@code null}
     */
    public record SubmissionLogRow(
            UUID attemptId,
            UUID companyId,
            String companyName,
            String transactionType,
            UUID documentId,
            int attemptNumber,
            String outcome,
            Integer statusCode,
            String errorSummary,
            OffsetDateTime submittedAt,
            OffsetDateTime completedAt,
            UUID submittedBy) {
    }
}
