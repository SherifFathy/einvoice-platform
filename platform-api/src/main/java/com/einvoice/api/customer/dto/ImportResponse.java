package com.einvoice.api.customer.dto;

import java.util.List;

/** Response body for customer Excel import results. */
public record ImportResponse(
        int totalRows,
        int importedCount,
        int errorCount,
        List<ImportError> errors
) {}
