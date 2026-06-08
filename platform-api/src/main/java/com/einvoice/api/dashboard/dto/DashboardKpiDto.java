package com.einvoice.api.dashboard.dto;

/**
 * Today / this-month KPI panel for the operator dashboard.
 *
 * @param today status breakdown for the current UTC day
 * @param thisMonth status breakdown for the current UTC month
 */
public record DashboardKpiDto(
        StatusBreakdownDto today,
        StatusBreakdownDto thisMonth) {
}
