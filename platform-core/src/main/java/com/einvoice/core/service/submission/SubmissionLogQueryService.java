package com.einvoice.core.service.submission;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.core.repository.support.SubmissionAttemptSpecifications;
import com.einvoice.core.service.submission.SubmissionLogReadModels.SubmissionLogRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only query service for the unified submission log (Wave 9, US3) under
 * the company-less {@code AUTHORITY_SCOPED} scope (ADR-001). One query over
 * {@code submission_attempts} spans all four document classes; the active
 * {@code authority_environment_id} is the only hard isolation boundary, and an
 * optional {@code companyId} narrows to a single company within the env.
 *
 * <p>Company display names are resolved in a single batched
 * {@link CompanyRepository#findAllById(Iterable)} lookup per page (no N+1),
 * mirroring {@code DashboardQueryService#recentActivity}. An unfinalized
 * attempt (null {@code result}) surfaces as outcome {@code IN_FLIGHT}.
 */
@Service
@Transactional(readOnly = true)
public class SubmissionLogQueryService {

    private final SubmissionAttemptRepository submissionAttemptRepository;
    private final CompanyRepository companyRepository;

    /**
     * Constructs the submission-log query service.
     *
     * @param submissionAttemptRepository submission attempt repository
     * @param companyRepository company repository for display-name resolution
     */
    public SubmissionLogQueryService(
            SubmissionAttemptRepository submissionAttemptRepository,
            CompanyRepository companyRepository) {
        this.submissionAttemptRepository = submissionAttemptRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Returns a page of submission-log rows for the parsed query, scoped to the
     * active environment and sorted as requested by the pageable.
     *
     * @param query the parsed submission-log query
     * @param pageable paging and sort (callers apply {@code submittedAt DESC})
     * @return a page of submission-log rows, possibly empty
     */
    public Page<SubmissionLogRow> list(SubmissionLogQuery query, Pageable pageable) {
        if (query == null || query.envId() == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Specification<SubmissionAttempt> spec = OperationalRepositorySupport
                .<SubmissionAttempt>authorityEnvironmentIdEquals(query.envId());
        if (query.filterCompanyId() != null) {
            spec = spec.and(OperationalRepositorySupport
                    .companyIdEquals(query.filterCompanyId()));
        }
        if (query.transactionType() != null) {
            spec = spec.and(SubmissionAttemptSpecifications
                    .transactionTypeEquals(query.transactionType()));
        }
        if (query.inFlight()) {
            spec = spec.and(SubmissionAttemptSpecifications.inFlight());
        } else if (query.outcomeResult() != null) {
            spec = spec.and(SubmissionAttemptSpecifications
                    .resultEquals(query.outcomeResult()));
        }
        if (query.dateFrom() != null || query.dateTo() != null) {
            spec = spec.and(SubmissionAttemptSpecifications.submittedAtBetween(
                    query.dateFrom(), query.dateTo()));
        }

        Page<SubmissionAttempt> page =
                submissionAttemptRepository.findAll(spec, pageable);
        Map<UUID, String> nameByCompany = resolveNames(page.getContent());
        return page.map(attempt -> toRow(attempt, nameByCompany));
    }

    /**
     * Resolves display names for the distinct companies present on the page in
     * a single batched lookup.
     *
     * @param attempts the attempts on the current page
     * @return company id to English display name
     */
    private Map<UUID, String> resolveNames(List<SubmissionAttempt> attempts) {
        List<UUID> companyIds = attempts.stream()
                .map(SubmissionAttempt::getCompanyId)
                .distinct()
                .toList();
        if (companyIds.isEmpty()) {
            return Map.of();
        }
        return companyRepository.findAllById(companyIds).stream()
                .collect(Collectors.toMap(
                        Company::getId, Company::getNameEn, (a, b) -> a));
    }

    /**
     * Maps a submission attempt to its log row, resolving the company name and
     * deriving the {@code IN_FLIGHT} outcome.
     *
     * @param attempt the attempt
     * @param nameByCompany resolved company display names
     * @return the submission-log row
     */
    private static SubmissionLogRow toRow(SubmissionAttempt attempt,
            Map<UUID, String> nameByCompany) {
        return new SubmissionLogRow(
                attempt.getId(),
                attempt.getCompanyId(),
                nameByCompany.getOrDefault(attempt.getCompanyId(), ""),
                attempt.getTransactionType().name(),
                attempt.getDocumentId(),
                attempt.getAttemptNumber(),
                outcome(attempt.getResult()),
                attempt.getStatusCode(),
                attempt.getErrorSummary(),
                attempt.getSubmittedAt(),
                attempt.getCompletedAt(),
                attempt.getSubmittedBy());
    }

    /**
     * Maps a submission result to its log outcome, treating an unfinalized
     * attempt as {@code IN_FLIGHT}.
     *
     * @param result the attempt result, or {@code null} when in flight
     * @return the outcome label
     */
    private static String outcome(SubmissionResult result) {
        return result == null ? "IN_FLIGHT" : result.name();
    }
}
