package com.einvoice.core.authority;

/** Javadoc. */
public record AuthorityResponse(
        boolean success,
        String result,
        String etaUuid,
        String etaLongId,
        String etaSubmissionId,
        Integer statusCode,
        String errorSummary,
        String rawResponse
) {}
