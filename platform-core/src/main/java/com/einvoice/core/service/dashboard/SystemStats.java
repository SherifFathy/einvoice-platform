package com.einvoice.core.service.dashboard;

/**
 * Counts-only Admin-Mode system-stats read-model for the active authority
 * environment (Constitution VII.3 — Admin Mode never sees operational record
 * content; only these scalar counts).
 *
 * <p>Maps 1:1 onto the {@code GET /api/admin/stats} wire shape defined in
 * {@code specs/012-wave9-dashboard-logs-hardening/contracts/dashboard.md}.
 *
 * @param authorityEnvironmentId the active authority environment the counts are
 *        scoped to (companies and submissions); users are global
 * @param totalCompanies distinct active companies registered (via an active
 *        transaction role) in the environment
 * @param totalUsers active users across the whole platform (global, not
 *        env-scoped — the {@code users} table has no environment dimension)
 * @param totalSubmissionsToday submission attempts submitted in the environment
 *        since the start of the current UTC day (half-open
 *        {@code [startOfUtcDay, startOfNextUtcDay)})
 */
public record SystemStats(
        Short authorityEnvironmentId,
        long totalCompanies,
        long totalUsers,
        long totalSubmissionsToday) {
}
