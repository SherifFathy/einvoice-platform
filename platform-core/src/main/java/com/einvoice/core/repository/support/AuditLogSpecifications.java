package com.einvoice.core.repository.support;

import com.einvoice.core.domain.shared.AuditLog;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification factories for {@link AuditLog} read queries that drive the
 * authority-scoped audit-log viewer (Wave 9, US4). Environment and
 * (optional) company scoping reuse {@link OperationalRepositorySupport}; the
 * factories here cover the audit-log-specific filters (entity type/id and the
 * {@code createdAt} window). The repository stays append-only — these
 * specifications feed the inherited {@code JpaSpecificationExecutor#findAll}
 * read path and never mutate rows (Constitution IX.3/IX.4).
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    /**
     * Filters by audited entity type.
     *
     * @param entityType the entity type label
     * @return a specification matching the given entity type
     */
    public static Specification<AuditLog> entityTypeEquals(String entityType) {
        return (Root<AuditLog> root, CriteriaQuery<?> query,
                CriteriaBuilder cb) ->
                cb.equal(root.get("entityType"), entityType);
    }

    /**
     * Filters by audited entity id.
     *
     * @param entityId the entity id (stored as text)
     * @return a specification matching the given entity id
     */
    public static Specification<AuditLog> entityIdEquals(String entityId) {
        return (Root<AuditLog> root, CriteriaQuery<?> query,
                CriteriaBuilder cb) ->
                cb.equal(root.get("entityId"), entityId);
    }

    /**
     * Filters by a closed {@code createdAt} window. Either bound may be null to
     * leave that side open.
     *
     * @param from inclusive lower bound on {@code createdAt} (nullable)
     * @param to inclusive upper bound on {@code createdAt} (nullable)
     * @return a specification constraining {@code createdAt} to the window
     */
    public static Specification<AuditLog> createdAtBetween(
            OffsetDateTime from, OffsetDateTime to) {
        return (Root<AuditLog> root, CriteriaQuery<?> query,
                CriteriaBuilder cb) -> {
            Predicate pred = cb.conjunction();
            if (from != null) {
                pred = cb.and(pred, cb.greaterThanOrEqualTo(
                        root.get("createdAt"), from));
            }
            if (to != null) {
                pred = cb.and(pred, cb.lessThanOrEqualTo(
                        root.get("createdAt"), to));
            }
            return pred;
        };
    }
}
