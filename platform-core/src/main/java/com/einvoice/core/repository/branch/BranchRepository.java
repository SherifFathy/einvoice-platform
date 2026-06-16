package com.einvoice.core.repository.branch;

import com.einvoice.core.domain.branch.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByCompanyIdAndIsActiveTrue(UUID companyId);

    @Query("""
            select b from Branch b
            join fetch b.company c
            where b.isActive = true
              and c.isActive = true
              and c.id in :companyIds
            order by c.nameEn asc, b.nameEn asc
            """)
    List<Branch> findVisibleActiveBranches(List<UUID> companyIds);

    Optional<Branch> findByCompanyIdAndBranchCode(UUID companyId, String branchCode);
}
