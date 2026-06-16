package com.einvoice.api.platform.dto;

import java.time.OffsetDateTime;

/** Current platform branding state. */
public record PlatformBrandingResponse(
        boolean logoConfigured,
        String logoMime,
        OffsetDateTime updatedAt) {
}
