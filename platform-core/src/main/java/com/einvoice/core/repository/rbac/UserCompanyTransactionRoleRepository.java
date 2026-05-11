package com.einvoice.core.repository.rbac;

import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface UserCompanyTransactionRoleRepository extends JpaRepository<UserCompanyTransactionRole, UUID> {

    List<UserCompanyTransactionRole> findByUserIdAndAuthorityEnvironmentIdAndIsActiveTrue(
            UUID userId, Short authorityEnvironmentId);

    List<UserCompanyTransactionRole> findByCompanyIdAndAuthorityEnvironmentIdAndIsActiveTrue(
            UUID companyId, Short authorityEnvironmentId);

    Optional<UserCompanyTransactionRole> findByUserIdAndCompanyIdAndAuthorityEnvironmentIdAndTransactionType(
            UUID userId, UUID companyId, Short authorityEnvironmentId, String transactionType);

    List<UserCompanyTransactionRole> findByUserIdAndCompanyIdAndAuthorityEnvironmentIdAndIsActiveTrue(
            UUID userId, UUID companyId, Short authorityEnvironmentId);

    @Query("SELECT DISTINCT uctr.company.id FROM UserCompanyTransactionRole uctr "
            + "WHERE uctr.authorityEnvironmentId = :authEnvId AND uctr.isActive = true")
    List<UUID> findDistinctCompanyIdsByAuthorityEnvironmentIdAndIsActiveTrue(
            Short authEnvId);

    @Query("SELECT DISTINCT uctr.company.id FROM UserCompanyTransactionRole uctr "
            + "WHERE uctr.user.id = :userId AND uctr.authorityEnvironmentId = :authEnvId "
            + "AND uctr.isActive = true")
    List<UUID> findDistinctAssignedCompanyIds(UUID userId, Short authEnvId);

    List<UserCompanyTransactionRole> findByUserIdAndIsActiveTrue(UUID userId);
}
