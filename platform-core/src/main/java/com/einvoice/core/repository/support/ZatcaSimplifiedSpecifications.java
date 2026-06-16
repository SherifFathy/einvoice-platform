package com.einvoice.core.repository.support;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Specification factories for ZATCA simplified invoice queries. */
public final class ZatcaSimplifiedSpecifications {

    private ZatcaSimplifiedSpecifications() {
    }

    public static Specification<ZatcaSimplifiedHeader> forCompany(UUID companyId) {
        return (Root<ZatcaSimplifiedHeader> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("companyId"), companyId);
    }

    /**
     * Filter by branch.
     *
     * @param branchId the branch identifier
     * @return a specification
     */
    public static Specification<ZatcaSimplifiedHeader> forBranch(UUID branchId) {
        return (Root<ZatcaSimplifiedHeader> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("branchId"), branchId);
    }

    public static Specification<ZatcaSimplifiedHeader> inState(DocumentState state) {
        return (Root<ZatcaSimplifiedHeader> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("status"), state);
    }

    /**
     * Filter by issue date range.
     *
     * @param from inclusive start date (nullable)
     * @param to inclusive end date (nullable)
     * @return a specification
     */
    public static Specification<ZatcaSimplifiedHeader> issuedBetween(LocalDate from, LocalDate to) {
        return (Root<ZatcaSimplifiedHeader> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Predicate pred = cb.conjunction();
            if (from != null) {
                pred = cb.and(pred, cb.greaterThanOrEqualTo(root.get("issueDate"), from));
            }
            if (to != null) {
                pred = cb.and(pred, cb.lessThanOrEqualTo(root.get("issueDate"), to));
            }
            return pred;
        };
    }

    public static Specification<ZatcaSimplifiedHeader> inActiveTenantAndAssignedCompany(
            Collection<UUID> assigned, Short authorityEnvironmentId) {
        return OperationalRepositorySupport.<ZatcaSimplifiedHeader>authorityEnvironmentIdEquals(authorityEnvironmentId)
                .and(OperationalRepositorySupport.companyIdIn(assigned));
    }
}
