package com.einvoice.core.repository;

import com.einvoice.core.domain.OnboardingProgress;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for accessing {@link OnboardingProgress} entities. */
@Repository
public interface OnboardingProgressRepository extends JpaRepository<OnboardingProgress, Long> {

    Optional<OnboardingProgress> findByBranchIdAndAuthorityAndEnvironment(
            Long branchId, Authority authority, Environment environment);

    boolean existsByBranchIdAndAuthorityAndEnvironment(
            Long branchId, Authority authority, Environment environment);
}
