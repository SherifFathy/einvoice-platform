package com.einvoice.api.submission.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

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
public record SubmissionLogRowDto(
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
