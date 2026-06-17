package com.einvoice.core.repository.rbac;

import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Counts the distinct active companies registered (via an active transaction
     * role) in an authority environment. Drives the Admin-Mode
     * {@code totalCompanies} stat (Constitution VII.3 — counts only). Mirrors
     * the {@code findDistinctCompanyIds…} selection so the count and the US1
     * card set share one env-scoped definition.
     *
     * @param authEnvId the active authority environment
     * @return the number of distinct active companies in the environment
     */
    @Query("SELECT COUNT(DISTINCT uctr.company.id) FROM UserCompanyTransactionRole uctr "
            + "WHERE uctr.authorityEnvironmentId = :authEnvId AND uctr.isActive = true")
    long countDistinctCompanyIdsByAuthorityEnvironmentIdAndIsActiveTrue(
            @Param("authEnvId") Short authEnvId);

    @Query("SELECT DISTINCT uctr.company.id FROM UserCompanyTransactionRole uctr "
            + "WHERE uctr.user.id = :userId AND uctr.authorityEnvironmentId = :authEnvId "
            + "AND uctr.isActive = true")
    List<UUID> findDistinctAssignedCompanyIds(UUID userId, Short authEnvId);

    List<UserCompanyTransactionRole> findByUserIdAndIsActiveTrue(UUID userId);
}
