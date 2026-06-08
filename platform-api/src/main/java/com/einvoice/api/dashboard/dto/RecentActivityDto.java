package com.einvoice.api.dashboard.dto;

import java.util.List;

/**
 * Aggregate response for {@code GET /api/dashboard/recent-activity}.
 *
 * @param entries newest-first submission attempts (at most 10)
 */
public record RecentActivityDto(
        List<RecentActivityEntryDto> entries) {
}
