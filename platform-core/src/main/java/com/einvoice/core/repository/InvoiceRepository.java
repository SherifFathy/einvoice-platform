package com.einvoice.core.repository;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Tenant-scoped repository for {@link Invoice} data access. */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndCompanyId(UUID id, Long companyId);

    @Query("SELECT i FROM Invoice i WHERE i.company.id = :companyId "
            + "AND (:status IS NULL OR i.status = :status) "
            + "AND (:type IS NULL OR i.type = :type) "
            + "AND (:dateFrom IS NULL OR i.issueDate >= :dateFrom) "
            + "AND (:dateTo IS NULL OR i.issueDate <= :dateTo) "
            + "AND (:search IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Invoice> findByCompanyIdFiltered(@Param("companyId") Long companyId,
            @Param("status") InvoiceStatus status,
            @Param("type") InvoiceType type,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("search") String search,
            Pageable pageable);

    boolean existsByBuyerIdAndCompanyIdAndStatusNot(Long buyerId,
            Long companyId, InvoiceStatus status);
}
