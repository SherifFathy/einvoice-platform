package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA simplified invoice headers. */
@Repository
public interface ZatcaSimplifiedHeaderRepository
        extends JpaRepository<ZatcaSimplifiedHeader, UUID>,
        JpaSpecificationExecutor<ZatcaSimplifiedHeader> {

    List<ZatcaSimplifiedHeader> findByCompanyIdAndAuthorityEnvironmentIdAndSellerVatNumber(
            UUID companyId, Short authorityEnvironmentId, String sellerVatNumber);

    boolean existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
            UUID companyId, Short authorityEnvironmentId, String invoiceNumber);

    Optional<ZatcaSimplifiedHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
            UUID companyId, Short authorityEnvironmentId, String invoiceNumber);

    /**
     * Counts ZATCA simplified headers grouped by company and status for an
     * environment. Used by the dashboard to derive per-company pending counts.
     *
     * @param authorityEnvironmentId the active authority environment
     * @return rows of {@code [companyId (UUID), status (DocumentState), count (Long)]}
     */
    @Query("SELECT h.companyId, h.status, COUNT(h.id) FROM ZatcaSimplifiedHeader h "
            + "WHERE h.authorityEnvironmentId = :envId "
            + "GROUP BY h.companyId, h.status")
    List<Object[]> countByCompanyGroupedByStatus(
            @Param("envId") Short authorityEnvironmentId);

    /**
     * Returns the {@code [companyId, documentId]} pairs of every REJECTED ZATCA
     * simplified invoice in an environment. Used by the dashboard failed-count rule.
     *
     * @param authorityEnvironmentId the active authority environment
     * @return rows of {@code [companyId (UUID), documentId (UUID)]}
     */
    @Query("SELECT h.companyId, h.id FROM ZatcaSimplifiedHeader h "
            + "WHERE h.authorityEnvironmentId = :envId "
            + "AND h.status = com.einvoice.core.domain.shared.DocumentState.REJECTED")
    List<Object[]> findRejectedDocumentIdsByEnv(
            @Param("envId") Short authorityEnvironmentId);

    /**
     * Counts ZATCA simplified headers grouped by status within a UTC half-open
     * window (compared against the {@code issueDate} calendar date), scoped to an
     * environment. Used by the dashboard today / this-month KPIs.
     *
     * @param authorityEnvironmentId the active authority environment
     * @param from inclusive lower bound on {@code issueDate}
     * @param to exclusive upper bound on {@code issueDate}
     * @return rows of {@code [status (DocumentState), count (Long)]}
     */
    @Query("SELECT h.status, COUNT(h.id) FROM ZatcaSimplifiedHeader h "
            + "WHERE h.authorityEnvironmentId = :envId "
            + "AND h.issueDate >= :from AND h.issueDate < :to "
            + "GROUP BY h.status")
    List<Object[]> countByStatusForEnvBetween(
            @Param("envId") Short authorityEnvironmentId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
