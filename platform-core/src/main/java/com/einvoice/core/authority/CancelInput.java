package com.einvoice.core.authority;

import java.util.UUID;

/** Javadoc. */
public record CancelInput(
        UUID companyId,
        Short authorityEnvironmentId,
        String transactionType,
        UUID documentId,
        String etaUuid,
        String reason
) {}
