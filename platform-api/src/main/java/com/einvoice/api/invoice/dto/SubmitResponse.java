package com.einvoice.api.invoice.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SubmitResponse(
        String invoiceId,
        String status,
        int attemptNumber,
        String authority,
        List<String> warnings,
        List<ErrorDetail> errors,
        OffsetDateTime submittedAt,
        OffsetDateTime completedAt
) {
    public record ErrorDetail(String code, String message) {}
}
