package com.einvoice.api.item.dto;

import java.util.List;

public record ImportResponse(
        int totalRows,
        int importedCount,
        int errorCount,
        List<ImportError> errors
) {}
