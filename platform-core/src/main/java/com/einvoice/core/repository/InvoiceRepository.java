package com.einvoice.core.repository;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Tenant-scoped repository for {@link Invoice} data access. */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>,
        JpaSpecificationExecutor<Invoice> {

    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.company.id = :companyId "
            + "AND (:lovContextId IS NULL OR i.lovContextId = :lovContextId)")
    Optional<Invoice> findByIdAndCompanyId(@Param("id") UUID id,
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId);

    boolean existsByBuyerIdAndCompanyIdAndStatusNot(Long buyerId,
            Long companyId, InvoiceStatus status);

    List<Invoice> findByStatusAndAuthority(InvoiceStatus status, Authority authority);

    @Query("SELECT COUNT(i) FROM Invoice i "
            + "WHERE i.status = :status AND i.authority = :authority "
            + "AND i.company.id = :companyId")
    long countByStatusAndAuthorityAndCompanyId(
            @Param("status") InvoiceStatus status,
            @Param("authority") Authority authority,
            @Param("companyId") Long companyId);
}
