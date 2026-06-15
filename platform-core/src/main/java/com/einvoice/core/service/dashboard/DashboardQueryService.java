package com.einvoice.core.service.dashboard;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.config.ZatcaConfig;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.config.ZatcaConfigRepository;
import com.einvoice.core.repository.eta.EtaInvoiceHeaderRepository;
import com.einvoice.core.repository.eta.EtaReceiptHeaderRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.core.repository.zatca.ZatcaSimplifiedHeaderRepository;
import com.einvoice.core.repository.zatca.ZatcaStandardHeaderRepository;
import com.einvoice.core.service.dashboard.DashboardReadModels.CertificateStatus;
import com.einvoice.core.service.dashboard.DashboardReadModels.CompanyCard;
import com.einvoice.core.service.dashboard.DashboardReadModels.DashboardKpi;
import com.einvoice.core.service.dashboard.DashboardReadModels.DashboardSummary;
import com.einvoice.core.service.dashboard.DashboardReadModels.RecentActivity;
import com.einvoice.core.service.dashboard.DashboardReadModels.RecentActivityEntry;
import com.einvoice.core.service.dashboard.DashboardReadModels.StatusBreakdown;
import com.einvoice.core.util.UtcDateRange;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Wave 9 operator dashboard read-models under the company-less
 * {@link TenantContext.Mode#AUTHORITY_SCOPED} scope (ADR-001): every accessible
 * company registered in the active {@code authority_environment_id} appears as a
 * card, and every operational query is filtered by that environment only —
 * environment isolation is the single hard boundary.
 *
 * <p>Counts follow the dashboard contract exactly:
 * <ul>
 *   <li>{@code pendingCount} = documents in {@code SUBMITTING/SUBMITTED/IN_REVIEW}.</li>
 *   <li>{@code failedCount} = documents in {@code REJECTED} <em>union</em>
 *       documents whose latest submission attempt is {@code ERROR/TIMEOUT}
 *       (no double-counting).</li>
 *   <li>KPI {@code today}/{@code thisMonth} windows are computed in UTC via
 *       {@link UtcDateRange}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    /**
     * Maximum number of entries returned by the recent-activity feed.
     */
    public static final int RECENT_ACTIVITY_LIMIT = 10;

    private static final EnumSet<DocumentState> PENDING_STATES = EnumSet.of(
            DocumentState.SUBMITTING,
            DocumentState.SUBMITTED,
            DocumentState.IN_REVIEW);

    private static final List<SubmissionResult> FAILED_BY_ATTEMPT_RESULTS = List.of(
            SubmissionResult.ERROR,
            SubmissionResult.TIMEOUT);

    private final EtaInvoiceHeaderRepository etaInvoiceRepository;
    private final EtaReceiptHeaderRepository etaReceiptRepository;
    private final ZatcaStandardHeaderRepository zatcaStandardRepository;
    private final ZatcaSimplifiedHeaderRepository zatcaSimplifiedRepository;
    private final SubmissionAttemptRepository submissionAttemptRepository;
    private final ZatcaConfigRepository zatcaConfigRepository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;
    private final CertificateExpiryEvaluator certificateEvaluator;
    private final Clock clock;

    /**
     * Constructs the dashboard query service with its repositories and helpers.
     *
     * @param etaInvoiceRepository ETA invoice header repository
     * @param etaReceiptRepository ETA receipt header repository
     * @param zatcaStandardRepository ZATCA standard header repository
     * @param zatcaSimplifiedRepository ZATCA simplified header repository
     * @param submissionAttemptRepository submission attempt repository
     * @param zatcaConfigRepository ZATCA signing-config repository
     * @param uctrRepository user-company-role repository (resolves the card set)
     * @param companyRepository company repository
     * @param certificateEvaluator certificate-expiry evaluator
     * @param clock UTC clock for KPI and certificate boundaries
     */
    public DashboardQueryService(
            EtaInvoiceHeaderRepository etaInvoiceRepository,
            EtaReceiptHeaderRepository etaReceiptRepository,
            ZatcaStandardHeaderRepository zatcaStandardRepository,
            ZatcaSimplifiedHeaderRepository zatcaSimplifiedRepository,
            SubmissionAttemptRepository submissionAttemptRepository,
            ZatcaConfigRepository zatcaConfigRepository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository,
            CertificateExpiryEvaluator certificateEvaluator,
            Clock clock) {
        this.etaInvoiceRepository = etaInvoiceRepository;
        this.etaReceiptRepository = etaReceiptRepository;
        this.zatcaStandardRepository = zatcaStandardRepository;
        this.zatcaSimplifiedRepository = zatcaSimplifiedRepository;
        this.submissionAttemptRepository = submissionAttemptRepository;
        this.zatcaConfigRepository = zatcaConfigRepository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
        this.certificateEvaluator = certificateEvaluator;
        this.clock = clock;
    }

    /**
     * Builds the dashboard summary: one card per accessible company in the
     * active environment plus the UTC today / this-month KPI panel.
     *
     * @param env the active authority environment
     * @param superUser whether the caller is a super user (sees all companies)
     * @return the summary read-model (empty cards and zeroed KPIs when no env)
     */
    public DashboardSummary summary(Short env, boolean superUser) {
        if (env == null) {
            return emptySummary();
        }
        final List<Company> companies = resolveCompanies(env, superUser);

        Map<UUID, Long> pendingByCompany = new HashMap<>();
        addPendingCounts(pendingByCompany,
                etaInvoiceRepository.countByCompanyGroupedByState(env));
        addPendingCounts(pendingByCompany,
                etaReceiptRepository.countByCompanyGroupedByState(env));
        addPendingCounts(pendingByCompany,
                zatcaStandardRepository.countByCompanyGroupedByStatus(env));
        addPendingCounts(pendingByCompany,
                zatcaSimplifiedRepository.countByCompanyGroupedByStatus(env));

        Map<UUID, Set<UUID>> failedDocsByCompany = new HashMap<>();
        addRejectedDocIds(failedDocsByCompany,
                etaInvoiceRepository.findRejectedDocumentIdsByEnv(env));
        addRejectedDocIds(failedDocsByCompany,
                etaReceiptRepository.findRejectedDocumentIdsByEnv(env));
        addRejectedDocIds(failedDocsByCompany,
                zatcaStandardRepository.findRejectedDocumentIdsByEnv(env));
        addRejectedDocIds(failedDocsByCompany,
                zatcaSimplifiedRepository.findRejectedDocumentIdsByEnv(env));
        List<UUID> companyIds = companies.stream().map(Company::getId).toList();
        if (!companyIds.isEmpty()) {
            List<SubmissionAttempt> failedLatest = submissionAttemptRepository
                    .findLatestAttemptsByResult(env, companyIds, FAILED_BY_ATTEMPT_RESULTS);
            for (SubmissionAttempt attempt : failedLatest) {
                failedDocsByCompany
                        .computeIfAbsent(attempt.getCompanyId(), k -> new HashSet<>())
                        .add(attempt.getDocumentId());
            }
        }
        Map<UUID, LocalDate> certExpiryByCompany = zatcaConfigRepository
                .findByAuthorityEnvironmentIdAndIsActiveTrue(env).stream()
                .filter(c -> c.getCertificateExpiryDate() != null)
                .collect(Collectors.toMap(
                        ZatcaConfig::getCompanyId,
                        ZatcaConfig::getCertificateExpiryDate,
                        (a, b) -> a));

        List<CompanyCard> cards = new ArrayList<>();
        for (Company company : companies) {
            UUID id = company.getId();
            int pending = pendingByCompany.getOrDefault(id, 0L).intValue();
            int failed = failedDocsByCompany.getOrDefault(id, Set.of()).size();
            CertificateStatus certificate =
                    certificateEvaluator.evaluate(certExpiryByCompany.get(id));
            cards.add(new CompanyCard(
                    id,
                    company.getNameEn(),
                    company.getNameAr(),
                    company.getTaxNumber(),
                    Boolean.TRUE.equals(company.getIsActive()),
                    pending,
                    failed,
                    certificate));
        }

        StatusBreakdown today = statusBreakdown(env, UtcDateRange.todayRange(clock));
        StatusBreakdown thisMonth = statusBreakdown(env, UtcDateRange.thisMonthRange(clock));
        return new DashboardSummary(cards, new DashboardKpi(today, thisMonth));
    }

    /**
     * Builds the recent-activity feed: the newest submission attempts across the
     * accessible companies in the active environment (at most 10).
     *
     * @param env the active authority environment
     * @param superUser whether the caller is a super user (sees all companies)
     * @return the activity read-model (possibly with an empty entry list)
     */
    public RecentActivity recentActivity(Short env, boolean superUser) {
        if (env == null) {
            return new RecentActivity(List.of());
        }
        final List<Company> companies = resolveCompanies(env, superUser);
        if (companies.isEmpty()) {
            return new RecentActivity(List.of());
        }
        List<UUID> companyIds = companies.stream().map(Company::getId).toList();
        Map<UUID, String> nameByCompany = companies.stream().collect(
                Collectors.toMap(Company::getId, Company::getNameEn, (a, b) -> a));
        List<SubmissionAttempt> attempts = submissionAttemptRepository.findRecentAttempts(
                env, companyIds, PageRequest.of(0, RECENT_ACTIVITY_LIMIT));
        List<RecentActivityEntry> entries = attempts.stream()
                .map(a -> new RecentActivityEntry(
                        a.getId(),
                        a.getCompanyId(),
                        nameByCompany.getOrDefault(a.getCompanyId(), ""),
                        a.getTransactionType().name(),
                        a.getDocumentId(),
                        outcome(a.getResult()),
                        a.getSubmittedAt()))
                .toList();
        return new RecentActivity(entries);
    }

    /**
     * Resolves the accessible company set for an environment under the
     * company-less model: all active companies registered (via UCTR) in the
     * environment, mirroring
     * {@code SessionContextAssembler.buildSuperUserCompanies}.
     *
     * @param env the active authority environment
     * @param superUser whether the caller is a super user
     * @return active companies registered in the environment
     */
    private List<Company> resolveCompanies(Short env, boolean superUser) {
        if (superUser) {
            // Super users operate across every active company, including ones
            // not yet assigned in this environment (mirrors
            // SessionContextAssembler.buildSuperUserCompanies).
            return companyRepository.findByIsActiveTrue();
        }
        List<UUID> ids = uctrRepository
                .findDistinctCompanyIdsByAuthorityEnvironmentIdAndIsActiveTrue(env);
        return companyRepository.findAllById(ids).stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .toList();
    }

    /**
     * Accumulates pending counts per company from grouped {@code [companyId,
     * state, count]} rows, summing only pending states.
     *
     * @param target per-company pending count accumulator
     * @param rows grouped repository rows
     */
    private void addPendingCounts(Map<UUID, Long> target, List<Object[]> rows) {
        for (Object[] row : rows) {
            UUID companyId = (UUID) row[0];
            DocumentState state = (DocumentState) row[1];
            Long count = (Long) row[2];
            if (PENDING_STATES.contains(state)) {
                target.merge(companyId, count, Long::sum);
            }
        }
    }

    /**
     * Accumulates rejected document ids per company from {@code [companyId,
     * documentId]} rows.
     *
     * @param target per-company rejected-document-set accumulator
     * @param rows rejected-document repository rows
     */
    private void addRejectedDocIds(Map<UUID, Set<UUID>> target, List<Object[]> rows) {
        for (Object[] row : rows) {
            UUID companyId = (UUID) row[0];
            UUID documentId = (UUID) row[1];
            target.computeIfAbsent(companyId, k -> new HashSet<>()).add(documentId);
        }
    }

    /**
     * Builds the status breakdown for the environment within a UTC half-open
     * window, aggregating across all four header tables.
     *
     * @param env the active authority environment
     * @param range UTC {@code [from, to)} window
     * @return the status breakdown with all seven states populated
     */
    private StatusBreakdown statusBreakdown(Short env, UtcDateRange.Range range) {
        Map<String, Integer> byStatus = new TreeMap<>();
        for (DocumentState state : DocumentState.values()) {
            byStatus.put(state.name(), 0);
        }
        LocalDate fromDate = range.from().toLocalDate();
        LocalDate toDate = range.to().toLocalDate();
        addStateCounts(byStatus,
                etaInvoiceRepository.countByStateForEnvBetween(env, range.from(), range.to()));
        addStateCounts(byStatus,
                etaReceiptRepository.countByStateForEnvBetween(env, range.from(), range.to()));
        addStateCounts(byStatus,
                zatcaStandardRepository.countByStatusForEnvBetween(env, fromDate, toDate));
        addStateCounts(byStatus,
                zatcaSimplifiedRepository.countByStatusForEnvBetween(env, fromDate, toDate));
        int total = byStatus.values().stream().mapToInt(Integer::intValue).sum();
        return new StatusBreakdown(total, byStatus);
    }

    /**
     * Accumulates per-status counts from grouped {@code [state, count]} rows.
     *
     * @param target status-count accumulator
     * @param rows grouped repository rows
     */
    private void addStateCounts(Map<String, Integer> target, List<Object[]> rows) {
        for (Object[] row : rows) {
            DocumentState state = (DocumentState) row[0];
            Long count = (Long) row[1];
            target.merge(state.name(), count.intValue(), Integer::sum);
        }
    }

    /**
     * Maps a submission result to its dashboard outcome string, treating an
     * unfinalized attempt as {@code IN_FLIGHT}.
     *
     * @param result the attempt result, or {@code null} when in flight
     * @return the outcome label
     */
    private String outcome(SubmissionResult result) {
        return result == null ? "IN_FLIGHT" : result.name();
    }

    /**
     * Builds an empty summary with zeroed KPIs and no cards.
     *
     * @return an empty summary
     */
    private DashboardSummary emptySummary() {
        StatusBreakdown empty = new StatusBreakdown(0, emptyStatusMap());
        return new DashboardSummary(List.of(), new DashboardKpi(empty, empty));
    }

    /**
     * Builds a status map with all seven states zeroed.
     *
     * @return a zeroed status map
     */
    private Map<String, Integer> emptyStatusMap() {
        Map<String, Integer> byStatus = new TreeMap<>();
        for (DocumentState state : DocumentState.values()) {
            byStatus.put(state.name(), 0);
        }
        return byStatus;
    }
}
