package com.einvoice.core.repository;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for accessing {@link AuthorityConfig} entities. */
@Repository
public interface AuthorityConfigRepository extends JpaRepository<AuthorityConfig, Long> {

    List<AuthorityConfig> findByBranchId(Long branchId);

    List<AuthorityConfig> findByBranchIdAndIsActiveTrue(Long branchId);

    Optional<AuthorityConfig> findByBranchIdAndAuthorityAndEnvironment(
            Long branchId, Authority authority, Environment environment);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ac FROM AuthorityConfig ac "
            + "WHERE ac.branch.id = :branchId "
            + "AND ac.authority = :authority "
            + "AND ac.environment = :environment")
    Optional<AuthorityConfig> findWithLockByBranchIdAndAuthorityAndEnvironment(
            @Param("branchId") Long branchId,
            @Param("authority") Authority authority,
            @Param("environment") Environment environment);

    boolean existsByBranchIdAndAuthorityAndEnvironment(
            Long branchId, Authority authority, Environment environment);
}
