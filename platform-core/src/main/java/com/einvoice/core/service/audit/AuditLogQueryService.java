package com.einvoice.core.service.audit;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.shared.AuditLog;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.shared.AuditLogRepository;
import com.einvoice.core.repository.support.AuditLogSpecifications;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.core.service.audit.AuditLogReadModels.AuditLogRow;
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
 * Read-only query service for the authority-scoped audit-log viewer (Wave 9,
 * US4) under the company-less {@code AUTHORITY_SCOPED} scope (ADR-001). The
 * active {@code authority_environment_id} is the only hard isolation boundary;
 * an optional {@code companyId} narrows to a single company within the env
 * (the default is cross-company).
 *
 * <p>Company display names are resolved in a single batched
 * {@link CompanyRepository#findAllById(Iterable)} lookup per page (no N+1),
 * mirroring {@code DashboardQueryService#recentActivity} and the submission-log
 * query service. The repository stays append-only: this service only ever
 * issues reads via the inherited {@code JpaSpecificationExecutor#findAll}
 * (Constitution IX.3/IX.4 — three-layer defence).
 */
@Service
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final CompanyRepository companyRepository;

    /**
     * Constructs the audit-log query service.
     *
     * @param auditLogRepository audit log repository (append-only reads)
     * @param companyRepository company repository for display-name resolution
     */
    public AuditLogQueryService(AuditLogRepository auditLogRepository,
            CompanyRepository companyRepository) {
        this.auditLogRepository = auditLogRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Returns a page of audit-log rows for the parsed query, scoped to the
     * active environment and sorted as requested by the pageable (callers
     * apply {@code createdAt DESC}).
     *
     * @param query the parsed audit-log query
     * @param pageable paging and sort
     * @return a page of audit-log rows, possibly empty
     */
    public Page<AuditLogRow> list(AuditLogQuery query, Pageable pageable) {
        if (query == null || query.envId() == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Specification<AuditLog> spec = OperationalRepositorySupport
                .<AuditLog>authorityEnvironmentIdEquals(query.envId());
        if (query.companyId() != null) {
            spec = spec.and(OperationalRepositorySupport
                    .companyIdEquals(query.companyId()));
        }
        if (query.entityType() != null) {
            spec = spec.and(AuditLogSpecifications
                    .entityTypeEquals(query.entityType()));
        }
        if (query.entityId() != null) {
            spec = spec.and(AuditLogSpecifications
                    .entityIdEquals(query.entityId()));
        }
        if (query.dateFrom() != null || query.dateTo() != null) {
            spec = spec.and(AuditLogSpecifications.createdAtBetween(
                    query.dateFrom(), query.dateTo()));
        }

        Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
        Map<UUID, String> nameByCompany = resolveNames(page.getContent());
        return page.map(log -> toRow(log, nameByCompany));
    }

    /**
     * Resolves display names for the distinct companies present on the page in
     * a single batched lookup.
     *
     * @param logs the audit rows on the current page
     * @return company id to English display name
     */
    private Map<UUID, String> resolveNames(List<AuditLog> logs) {
        List<UUID> companyIds = logs.stream()
                .map(AuditLog::getCompanyId)
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
     * Maps an audit-log entity to its viewer row, resolving the company name.
     *
     * @param log the audit-log entity
     * @param nameByCompany resolved company display names
     * @return the audit-log row
     */
    private static AuditLogRow toRow(AuditLog log,
            Map<UUID, String> nameByCompany) {
        UUID companyId = log.getCompanyId();
        return new AuditLogRow(
                log.getId(),
                companyId,
                companyId != null
                        ? nameByCompany.getOrDefault(companyId, "")
                        : "",
                log.getUserId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getPayloadBefore(),
                log.getPayloadAfter(),
                log.getIpAddress(),
                log.getCreatedAt());
    }
}
