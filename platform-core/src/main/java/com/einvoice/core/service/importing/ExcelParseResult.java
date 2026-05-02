package com.einvoice.core.service.importing;

import java.util.List;

/**
 * Result of parsing an Excel file into entity rows.
 * Contains successfully parsed rows, per-row parsing errors, and the total
 * number of data rows encountered.
 *
 * @param <T> the entity type
 * @param totalRows total data rows in the spreadsheet (excluding header)
 * @param validRows rows that passed parsing and row-level validation
 * @param errors rows that failed parsing or validation
 */
public record ExcelParseResult<T>(int totalRows, List<ParsedRow<T>> validRows,
        List<RowError> errors) {
}
