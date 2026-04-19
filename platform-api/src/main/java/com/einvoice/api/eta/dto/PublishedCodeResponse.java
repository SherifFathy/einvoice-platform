package com.einvoice.api.eta.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for a published ETA code from the ETA directory.
 */
public record PublishedCodeResponse(
        String itemCode,
        String codeType,
        String description,
        String publishedBy,
        OffsetDateTime publishedAt
) {}
