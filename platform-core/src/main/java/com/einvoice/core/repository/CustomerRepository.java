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

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId "
            + "AND c.isActive = true "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId)")
    Page<Customer> findByCompanyIdAndIsActiveTrue(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.id = :id AND c.company.id = :companyId "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId)")
    Optional<Customer> findByIdAndCompanyId(@Param("id") Long id,
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId);

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId AND c.isActive = true "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId) "
            + "AND (LOWER(c.nameEn) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(c.nameAr) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(c.vatNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Customer> searchByCompanyId(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId,
            @Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId "
            + "AND c.isActive = true AND c.customerType = :customerType "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId)")
    Page<Customer> findByCompanyIdAndIsActiveTrueAndCustomerType(
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId,
            @Param("customerType") CustomerType customerType, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId "
            + "AND c.isActive = true AND c.vatNumber = :vatNumber "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId)")
    Optional<Customer> findByCompanyIdAndVatNumber(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId,
            @Param("vatNumber") String vatNumber);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Customer c "
            + "WHERE c.company.id = :companyId AND c.vatNumber = :vatNumber "
            + "AND c.isActive = true "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId)")
    boolean existsByCompanyIdAndVatNumberAndIsActiveTrue(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId,
            @Param("vatNumber") String vatNumber);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Customer c "
            + "WHERE c.company.id = :companyId AND c.id = :id AND c.isActive = true "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId)")
    boolean existsByCompanyIdAndIdAndIsActiveTrue(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId, @Param("id") Long id);

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId "
            + "AND c.isActive = true "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId) "
            + "ORDER BY c.nameEn ASC")
    List<Customer> findByCompanyIdAndIsActiveTrueOrderByNameEnAsc(
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId);

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId "
            + "AND c.isActive = true AND c.contactEmail = :contactEmail "
            + "AND (:lovContextId IS NULL OR c.lovContextId = :lovContextId)")
    Optional<Customer> findByCompanyIdAndContactEmail(
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId,
            @Param("contactEmail") String contactEmail);
}
