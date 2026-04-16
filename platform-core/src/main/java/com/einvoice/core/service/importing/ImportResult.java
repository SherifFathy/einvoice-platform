package com.einvoice.core.service.importing;

import java.util.List;

public record ImportResult(int totalRows, int importedCount, int errorCount,
        List<ImportError> errors) {
}
