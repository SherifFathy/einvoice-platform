package com.einvoice.core.repository;

import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.enums.CustomerType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for tenant-scoped customer data access. */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByCompanyIdAndIsActiveTrue(Long companyId, Pageable pageable);

    Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId AND c.isActive = true "
            + "AND (LOWER(c.nameEn) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(c.nameAr) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(c.vatNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Customer> searchByCompanyId(@Param("companyId") Long companyId,
            @Param("search") String search, Pageable pageable);

    Page<Customer> findByCompanyIdAndIsActiveTrueAndCustomerType(
            Long companyId, CustomerType customerType, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId AND c.isActive = true "
            + "AND c.vatNumber = :vatNumber")
    Optional<Customer> findByCompanyIdAndVatNumber(@Param("companyId") Long companyId,
            @Param("vatNumber") String vatNumber);

    boolean existsByCompanyIdAndVatNumberAndIsActiveTrue(Long companyId, String vatNumber);

    boolean existsByCompanyIdAndIdAndIsActiveTrue(Long companyId, Long id);

    List<Customer> findByCompanyIdAndIsActiveTrueOrderByNameEnAsc(Long companyId);
}
