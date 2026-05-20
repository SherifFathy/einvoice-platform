package com.einvoice.core.repository.support;

import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.document.EtaReceiptDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Specification factories for ETA receipt queries. */
public final class EtaReceiptSpecifications {

    private EtaReceiptSpecifications() {
    }

    /**
     * Filter by company.
     *
     * @param companyId the company identifier
     * @return a specification
     */
    public static Specification<EtaReceiptHeader> forCompany(UUID companyId) {
        return (Root<EtaReceiptHeader> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("companyId"), companyId);
    }

    /**
     * Filter by lifecycle state.
     *
     * @param state the receipt state
     * @return a specification
     */
    public static Specification<EtaReceiptHeader> inState(DocumentState state) {
        return (Root<EtaReceiptHeader> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("state"), state);
    }

    /**
     * Filter by document type.
     *
     * @param documentType the document type
     * @return a specification
     */
    public static Specification<EtaReceiptHeader> withDocumentType(
            EtaReceiptDocumentType documentType) {
        return (Root<EtaReceiptHeader> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("documentType"), documentType);
    }

    /**
     * Filter by issue date range.
     *
     * @param from inclusive start date (nullable)
     * @param to inclusive end date (nullable)
     * @return a specification
     */
    public static Specification<EtaReceiptHeader> issuedBetween(LocalDate from,
            LocalDate to) {
        return (Root<EtaReceiptHeader> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Predicate pred = cb.conjunction();
            if (from != null) {
                pred = cb.and(pred, cb.greaterThanOrEqualTo(
                        root.get("issueDatetime"), from.atStartOfDay()));
            }
            if (to != null) {
                pred = cb.and(pred, cb.lessThan(
                        root.get("issueDatetime"), to.plusDays(1).atStartOfDay()));
            }
            return pred;
        };
    }

    /**
     * Compound filter for assigned companies within an authority environment.
     *
     * @param assigned the assigned company IDs
     * @param authorityEnvironmentId the environment identifier
     * @return a specification combining both constraints
     */
    public static Specification<EtaReceiptHeader> inActiveTenantAndAssignedCompany(
            Collection<UUID> assigned, Short authorityEnvironmentId) {
        return OperationalRepositorySupport.<EtaReceiptHeader>authorityEnvironmentIdEquals(
                        authorityEnvironmentId)
                .and(OperationalRepositorySupport.companyIdIn(assigned));
    }
}
