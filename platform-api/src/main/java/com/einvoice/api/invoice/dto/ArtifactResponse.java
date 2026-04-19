package com.einvoice.api.invoice.dto;

import java.time.OffsetDateTime;

public record ArtifactResponse(
        Long id,
        String type,
        String contentHash,
        OffsetDateTime createdAt,
        String downloadUrl
) {}
