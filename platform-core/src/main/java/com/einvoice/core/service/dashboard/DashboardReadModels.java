package com.einvoice.core.service.dashboard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable read-model records returned by {@link DashboardQueryService} and
 * assembled into the Wave 9 operator dashboard responses.
 *
 * <p>These live in {@code platform-core} so the query service can return them
 * without depending on the {@code platform-api} layer; the dashboard controller
 * maps each record to its wire DTO. Every field mirrors the shapes defined in
 * {@code specs/012-wave9-dashboard-logs-hardening/contracts/dashboard.md}.
 */
public final class DashboardReadModels {

    private DashboardReadModels() {
    }

    /**
     * Derived certificate-expiry status. Never carries certificate bytes or key
     * material (Constitution VI/XVIII).
     *
     * @param daysRemaining expiry minus today (UTC); negative when already expired
     * @param expiringSoon {@code true} when {@code 0 <= daysRemaining < 30}
     * @param expired {@code true} when {@code daysRemaining < 0}
     */
    public record CertificateStatus(
            int daysRemaining,
            boolean expiringSoon,
            boolean expired) {
    }

    /**
     * One company card on the operator dashboard.
     *
     * @param companyId owning company
     * @param nameEn English company name
     * @param nameAr Arabic company name
     * @param taxNumber company tax number
     * @param active whether the company is active
     * @param pendingCount documents in {@code SUBMITTING/SUBMITTED/IN_REVIEW}
     * @param failedCount rejected documents plus documents whose latest
     *                    submission attempt ended in {@code ERROR/TIMEOUT}
     * @param certificate certificate status, or {@code null} when the company
     *                    has no signing certificate (ETA)
     */
    public record CompanyCard(
            UUID companyId,
            String nameEn,
            String nameAr,
            String taxNumber,
            boolean active,
            int pendingCount,
            int failedCount,
            CertificateStatus certificate) {
    }

    /**
     * Document counts grouped by {@link com.einvoice.core.domain.shared.DocumentState}.
     *
     * @param total sum of every status bucket
     * @param byStatus map keyed by {@link com.einvoice.core.domain.shared.DocumentState}
     *                 name to document count
     */
    public record StatusBreakdown(
            int total,
            Map<String, Integer> byStatus) {
    }

    /**
     * Today / this-month KPI panel.
     *
     * @param today status breakdown for the current UTC day
     * @param thisMonth status breakdown for the current UTC month
     */
    public record DashboardKpi(
            StatusBreakdown today,
            StatusBreakdown thisMonth) {
    }

    /**
     * Aggregate response for {@code GET /api/dashboard/summary}.
     *
     * @param cards one card per accessible company in the active environment
     * @param kpi today / this-month aggregate breakdown
     */
    public record DashboardSummary(
            List<CompanyCard> cards,
            DashboardKpi kpi) {
    }

    /**
     * One row in the recent-activity feed.
     *
     * @param attemptId submission attempt identifier
     * @param companyId owning company
     * @param companyName owning company display name
     * @param transactionType {@code INVOICE/RECEIPT/STANDARD/SIMPLIFIED}
     * @param documentId the transmitted document
     * @param outcome {@code SUCCESS/REJECTED/ERROR/TIMEOUT/AMBIGUOUS/IN_FLIGHT}
     * @param submittedAt UTC submission timestamp
     */
    public record RecentActivityEntry(
            UUID attemptId,
            UUID companyId,
            String companyName,
            String transactionType,
            UUID documentId,
            String outcome,
            OffsetDateTime submittedAt) {
    }

    /**
     * Aggregate response for {@code GET /api/dashboard/recent-activity}.
     *
     * @param entries newest-first submission attempts (at most 10)
     */
    public record RecentActivity(
            List<RecentActivityEntry> entries) {
    }
}
