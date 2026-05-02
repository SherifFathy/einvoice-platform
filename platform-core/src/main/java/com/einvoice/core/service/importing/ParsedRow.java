package com.einvoice.core.service.importing;

/**
 * Wraps a parsed entity together with its original row number
 * from the source spreadsheet.
 *
 * @param <T> the entity type
 * @param rowNum the 1-based row number in the source file
 * @param entity the parsed entity
 */
public record ParsedRow<T>(int rowNum, T entity) {
}
