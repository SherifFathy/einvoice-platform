package com.einvoice.api.dashboard.dto;

import java.util.Map;

/**
 * Document counts grouped by lifecycle status.
 *
 * @param total sum of every status bucket
 * @param byStatus map keyed by {@code DocumentState} name to document count
 */
public record StatusBreakdownDto(
        int total,
        Map<String, Integer> byStatus) {
}
