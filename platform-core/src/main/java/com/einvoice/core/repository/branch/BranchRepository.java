package com.einvoice.core.repository.branch;

import com.einvoice.core.domain.branch.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByCompanyIdAndIsActiveTrue(UUID companyId);

    Optional<Branch> findByCompanyIdAndBranchCode(UUID companyId, String branchCode);
}
