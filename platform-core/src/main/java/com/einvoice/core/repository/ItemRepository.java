package com.einvoice.core.repository;

import com.einvoice.core.domain.Item;
import com.einvoice.core.domain.enums.AuthorityScope;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Tenant-scoped repository for {@link Item} master data. */
@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query("SELECT i FROM Item i WHERE i.company.id = :companyId "
            + "AND i.isActive = true "
            + "AND (:lovContextId IS NULL OR i.lovContextId = :lovContextId)")
    Page<Item> findByCompanyIdAndIsActiveTrue(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.id = :id AND i.company.id = :companyId "
            + "AND (:lovContextId IS NULL OR i.lovContextId = :lovContextId)")
    Optional<Item> findByIdAndCompanyId(@Param("id") Long id,
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId);

    @Query("SELECT i FROM Item i WHERE i.company.id = :companyId AND i.isActive = true "
            + "AND (:lovContextId IS NULL OR i.lovContextId = :lovContextId) "
            + "AND (LOWER(i.nameEn) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(i.nameAr) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(i.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Item> searchByCompanyId(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId,
            @Param("search") String search, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.company.id = :companyId "
            + "AND i.isActive = true AND i.authorityScope = :authorityScope "
            + "AND (:lovContextId IS NULL OR i.lovContextId = :lovContextId)")
    Page<Item> findByCompanyIdAndIsActiveTrueAndAuthorityScope(
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId,
            @Param("authorityScope") AuthorityScope authorityScope, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.company.id = :companyId "
            + "AND i.code = :code AND i.isActive = true "
            + "AND (:lovContextId IS NULL OR i.lovContextId = :lovContextId)")
    Optional<Item> findByCompanyIdAndCodeAndIsActiveTrue(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId, @Param("code") String code);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Item i "
            + "WHERE i.company.id = :companyId AND i.code = :code "
            + "AND i.isActive = true "
            + "AND (:lovContextId IS NULL OR i.lovContextId = :lovContextId)")
    boolean existsByCompanyIdAndCodeAndIsActiveTrue(@Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId, @Param("code") String code);
}
