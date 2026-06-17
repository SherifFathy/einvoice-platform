package com.einvoice.api.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row in the recent-activity feed.
 *
 * @param attemptId submission attempt identifier
 * @param companyId owning company
 * @param companyName owning company display name
 * @param transactionType {@code INVOICE/RECEIPT/STANDARD/SIMPLIFIED}
 * @param documentId the transmitted document
 * @param outcome {@code SUCCESS/REJECTED/ERROR/TIMEOUT/AMBIGUOUS/IN_FLIGHT}
 * @param submittedAt UTC submission timestamp
 */
public record RecentActivityEntryDto(
        UUID attemptId,
        UUID companyId,
        String companyName,
        String transactionType,
        UUID documentId,
        String outcome,
        OffsetDateTime submittedAt) {
}
