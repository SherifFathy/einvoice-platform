package com.einvoice.api.integration.dto.shared;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ERP-reported lifecycle status. Mapped to internal DocumentState per FR-016.
 * Exhaustive: DRAFT, VALID, INVALID, CLEARED, REPORTED, REJECTED, FAILED, CANCELLED.
 */
@Schema(name = "IntegrationDocumentStatus", enumAsRef = true)
public enum IntegrationDocumentStatus {
    DRAFT,
    VALID,
    INVALID,
    CLEARED,
    REPORTED,
    REJECTED,
    FAILED,
    CANCELLED
}
