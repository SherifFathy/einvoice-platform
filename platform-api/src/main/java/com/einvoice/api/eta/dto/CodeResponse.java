package com.einvoice.api.eta.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for an ETA item code registration.
 */
public record CodeResponse(
        Long id,
        String itemCode,
        String codeType,
        String description,
        String status,
        String etaCodeId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
