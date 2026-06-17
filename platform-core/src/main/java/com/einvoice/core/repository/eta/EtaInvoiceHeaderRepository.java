package com.einvoice.core.repository.eta;

import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for ETA invoice headers, including dashboard aggregate queries. */
public interface EtaInvoiceHeaderRepository
        extends JpaRepository<EtaInvoiceHeader, UUID>,
        JpaSpecificationExecutor<EtaInvoiceHeader> {

    Optional<EtaInvoiceHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
            UUID companyId, Short authorityEnvironmentId, String invoiceNumber);

    /**
     * Counts ETA invoice headers grouped by company and state for an environment.
     * Used by the dashboard to derive per-company pending counts.
     *
     * @param authorityEnvironmentId the active authority environment
     * @return rows of {@code [companyId (UUID), state (DocumentState), count (Long)]}
     */
    @Query("SELECT h.companyId, h.state, COUNT(h.id) FROM EtaInvoiceHeader h "
            + "WHERE h.authorityEnvironmentId = :envId "
            + "GROUP BY h.companyId, h.state")
    List<Object[]> countByCompanyGroupedByState(
            @Param("envId") Short authorityEnvironmentId);

    /**
     * Returns the {@code [companyId, documentId]} pairs of every REJECTED ETA
     * invoice in an environment. Used by the dashboard failed-count rule.
     *
     * @param authorityEnvironmentId the active authority environment
     * @return rows of {@code [companyId (UUID), documentId (UUID)]}
     */
    @Query("SELECT h.companyId, h.id FROM EtaInvoiceHeader h "
            + "WHERE h.authorityEnvironmentId = :envId "
            + "AND h.state = com.einvoice.core.domain.shared.DocumentState.REJECTED")
    List<Object[]> findRejectedDocumentIdsByEnv(
            @Param("envId") Short authorityEnvironmentId);

    /**
     * Counts ETA invoice headers grouped by state within a UTC half-open window,
     * scoped to an environment. Used by the dashboard today / this-month KPIs.
     *
     * @param authorityEnvironmentId the active authority environment
     * @param from inclusive lower bound on {@code issueDatetime}
     * @param to exclusive upper bound on {@code issueDatetime}
     * @return rows of {@code [state (DocumentState), count (Long)]}
     */
    @Query("SELECT h.state, COUNT(h.id) FROM EtaInvoiceHeader h "
            + "WHERE h.authorityEnvironmentId = :envId "
            + "AND h.issueDatetime >= :from AND h.issueDatetime < :to "
            + "GROUP BY h.state")
    List<Object[]> countByStateForEnvBetween(
            @Param("envId") Short authorityEnvironmentId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
