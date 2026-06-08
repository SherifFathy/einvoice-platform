package com.einvoice.api.dashboard.dto;

import java.util.List;

/**
 * Aggregate response for {@code GET /api/dashboard/summary}.
 *
 * @param cards one card per accessible company in the active environment
 * @param kpi today / this-month aggregate breakdown
 */
public record DashboardSummaryDto(
        List<CompanyCardDto> cards,
        DashboardKpiDto kpi) {
}
