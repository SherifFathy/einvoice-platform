package com.einvoice.api.dashboard;

import com.einvoice.api.dashboard.dto.CertificateStatusDto;
import com.einvoice.api.dashboard.dto.CompanyCardDto;
import com.einvoice.api.dashboard.dto.DashboardKpiDto;
import com.einvoice.api.dashboard.dto.DashboardSummaryDto;
import com.einvoice.api.dashboard.dto.RecentActivityDto;
import com.einvoice.api.dashboard.dto.RecentActivityEntryDto;
import com.einvoice.api.dashboard.dto.StatusBreakdownDto;
import com.einvoice.core.service.dashboard.DashboardQueryService;
import com.einvoice.core.service.dashboard.DashboardReadModels.CertificateStatus;
import com.einvoice.core.service.dashboard.DashboardReadModels.CompanyCard;
import com.einvoice.core.service.dashboard.DashboardReadModels.DashboardKpi;
import com.einvoice.core.service.dashboard.DashboardReadModels.DashboardSummary;
import com.einvoice.core.service.dashboard.DashboardReadModels.RecentActivity;
import com.einvoice.core.service.dashboard.DashboardReadModels.RecentActivityEntry;
import com.einvoice.core.service.dashboard.DashboardReadModels.StatusBreakdown;
import com.einvoice.security.tenant.TenantContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wave 9 operator dashboard endpoints. Company-less and environment-scoped per
 * ADR-001: reachable in {@code AUTHORITY_SCOPED} (and {@code OPERATIONAL_MODE}),
 * with no {@code @RequiresPermission} VIEW gate. Environment isolation is the
 * only hard boundary; {@code ADMIN_MODE} is rejected upstream by the tenant
 * filter (admin-only stats live at {@code /api/admin/stats}).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardQueryService service;

    /**
     * Constructs the controller with its dashboard query service.
     *
     * @param service the dashboard query service
     */
    public DashboardController(DashboardQueryService service) {
        this.service = service;
    }

    /**
     * Returns the company cards and KPI panel for the active authority
     * environment.
     *
     * @return the dashboard summary DTO
     */
    @GetMapping("/summary")
    public DashboardSummaryDto summary() {
        return toDto(service.summary(TenantContext.getAuthorityEnvironmentId()));
    }

    /**
     * Returns the recent-activity feed for the active authority environment.
     *
     * @return the recent-activity DTO
     */
    @GetMapping("/recent-activity")
    public RecentActivityDto recentActivity() {
        return toDto(service.recentActivity(TenantContext.getAuthorityEnvironmentId()));
    }

    /**
     * Maps the summary read-model to its wire DTO.
     *
     * @param summary the summary read-model
     * @return the summary DTO
     */
    private DashboardSummaryDto toDto(DashboardSummary summary) {
        List<CompanyCardDto> cards = summary.cards().stream()
                .map(this::toDto)
                .toList();
        return new DashboardSummaryDto(cards, toDto(summary.kpi()));
    }

    /**
     * Maps a company card read-model to its wire DTO.
     *
     * @param card the card read-model
     * @return the card DTO
     */
    private CompanyCardDto toDto(CompanyCard card) {
        return new CompanyCardDto(
                card.companyId(),
                card.nameEn(),
                card.nameAr(),
                card.taxNumber(),
                card.active(),
                card.pendingCount(),
                card.failedCount(),
                toDto(card.certificate()));
    }

    /**
     * Maps a certificate status read-model to its wire DTO.
     *
     * @param certificate the certificate read-model, or {@code null}
     * @return the certificate DTO, or {@code null}
     */
    private CertificateStatusDto toDto(CertificateStatus certificate) {
        if (certificate == null) {
            return null;
        }
        return new CertificateStatusDto(
                certificate.daysRemaining(),
                certificate.expiringSoon(),
                certificate.expired());
    }

    /**
     * Maps a KPI read-model to its wire DTO.
     *
     * @param kpi the KPI read-model
     * @return the KPI DTO
     */
    private DashboardKpiDto toDto(DashboardKpi kpi) {
        return new DashboardKpiDto(toDto(kpi.today()), toDto(kpi.thisMonth()));
    }

    /**
     * Maps a status breakdown read-model to its wire DTO.
     *
     * @param breakdown the breakdown read-model
     * @return the breakdown DTO
     */
    private StatusBreakdownDto toDto(StatusBreakdown breakdown) {
        return new StatusBreakdownDto(breakdown.total(), breakdown.byStatus());
    }

    /**
     * Maps the recent-activity read-model to its wire DTO.
     *
     * @param activity the activity read-model
     * @return the activity DTO
     */
    private RecentActivityDto toDto(RecentActivity activity) {
        List<RecentActivityEntryDto> entries = activity.entries().stream()
                .map(this::toDto)
                .toList();
        return new RecentActivityDto(entries);
    }

    /**
     * Maps a recent-activity entry read-model to its wire DTO.
     *
     * @param entry the entry read-model
     * @return the entry DTO
     */
    private RecentActivityEntryDto toDto(RecentActivityEntry entry) {
        return new RecentActivityEntryDto(
                entry.attemptId(),
                entry.companyId(),
                entry.companyName(),
                entry.transactionType(),
                entry.documentId(),
                entry.outcome(),
                entry.submittedAt());
    }
}
