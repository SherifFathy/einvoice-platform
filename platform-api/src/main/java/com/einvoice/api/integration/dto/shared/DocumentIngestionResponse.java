package com.einvoice.api.integration.dto.shared;

import java.util.UUID;

public record DocumentIngestionResponse(
        UUID id,
        String documentNumber,
        String erpReferenceId,
        String internalStatus,
        String message
) {}
