package com.einvoice.core.repository;

import com.einvoice.core.domain.Branch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for Branch entity operations. */
@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByCompanyIdOrderByCreatedAtAsc(Long companyId);

    List<Branch> findByCompanyIdAndIsActiveTrueOrderByCreatedAtAsc(Long companyId);
}
