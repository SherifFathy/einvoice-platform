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

    Page<Item> findByCompanyIdAndIsActiveTrue(Long companyId, Pageable pageable);

    Optional<Item> findByIdAndCompanyId(Long id, Long companyId);

    @Query("SELECT i FROM Item i WHERE i.company.id = :companyId AND i.isActive = true "
            + "AND (LOWER(i.nameEn) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(i.nameAr) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(i.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Item> searchByCompanyId(@Param("companyId") Long companyId,
            @Param("search") String search, Pageable pageable);

    Page<Item> findByCompanyIdAndIsActiveTrueAndAuthorityScope(
            Long companyId, AuthorityScope authorityScope, Pageable pageable);

    Optional<Item> findByCompanyIdAndCodeAndIsActiveTrue(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIsActiveTrue(Long companyId, String code);
}
