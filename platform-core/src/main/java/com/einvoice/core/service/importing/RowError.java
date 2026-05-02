package com.einvoice.core.service.importing;

/**
 * Represents an error for a specific row in a bulk upload operation.
 *
 * @param row the 1-based row number in the source file
 * @param field the field name that caused the error
 * @param message a human-readable error description
 */
public record RowError(int row, String field, String message) {
}
