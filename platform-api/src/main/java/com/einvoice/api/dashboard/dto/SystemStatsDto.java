package com.einvoice.api.dashboard.dto;

/**
 * Counts-only response for {@code GET /api/admin/stats} (Admin-Mode / Super
 * User). Carries no operational record content (Constitution VII.3 / FR-007).
 *
 * @param authorityEnvironmentId the active authority environment the env-scoped
 *        counts are scoped to
 * @param totalCompanies distinct active companies registered in the environment
 * @param totalUsers active users across the whole platform (global)
 * @param totalSubmissionsToday submission attempts submitted in the environment
 *        since the start of the current UTC day
 */
public record SystemStatsDto(
        Short authorityEnvironmentId,
        long totalCompanies,
        long totalUsers,
        long totalSubmissionsToday) {
}
