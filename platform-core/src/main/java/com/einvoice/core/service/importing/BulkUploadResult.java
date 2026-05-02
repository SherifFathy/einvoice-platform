package com.einvoice.core.service.importing;

import java.util.List;

/**
 * Summary result of a bulk upload (create-batch) operation.
 *
 * @param processed total number of rows successfully persisted
 * @param failed total number of rows that could not be persisted
 * @param errors per-row error details
 */
public record BulkUploadResult(int processed, int failed, List<RowError> errors) {
}
