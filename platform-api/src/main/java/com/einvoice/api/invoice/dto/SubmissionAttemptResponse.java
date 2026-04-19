package com.einvoice.api.invoice.dto;

import java.time.OffsetDateTime;

public record SubmissionAttemptResponse(
        int attemptNumber,
        String authority,
        String environment,
        String result,
        Integer statusCode,
        String errorSummary,
        OffsetDateTime submittedAt,
        OffsetDateTime completedAt
) {}
