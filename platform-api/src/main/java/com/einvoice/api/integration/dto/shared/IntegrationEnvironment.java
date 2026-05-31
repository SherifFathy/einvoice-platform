package com.einvoice.api.integration.dto.shared;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Environments accepted by the ingestion gateway.
 * PRODUCTION is intentionally absent (FR-008).
 */
@Schema(name = "IntegrationEnvironment", enumAsRef = true)
public enum IntegrationEnvironment {
    SANDBOX,
    PREPROD
}
