package com.einvoice.core.authority;

import java.util.UUID;

/** Javadoc. */
public record StatusInput(
        UUID companyId,
        Short authorityEnvironmentId,
        String transactionType,
        UUID documentId,
        String etaSubmissionId
) {}
