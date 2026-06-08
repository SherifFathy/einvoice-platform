package com.einvoice.core.repository.config;

import com.einvoice.core.domain.config.ZatcaConfig;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA signing configurations. */
@Repository
public interface ZatcaConfigRepository
        extends JpaRepository<ZatcaConfig, UUID>, JpaSpecificationExecutor<ZatcaConfig> {

    @Query("SELECT e FROM ZatcaConfig e "
            + "WHERE e.companyId = :companyId "
            + "AND e.authorityEnvironmentId = :authorityEnvironmentId")
    Optional<ZatcaConfig> findByCompanyAndAuthorityEnvironment(
            @Param("companyId") UUID companyId,
            @Param("authorityEnvironmentId") Short authorityEnvironmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ZatcaConfig e "
            + "WHERE e.companyId = :companyId "
            + "AND e.authorityEnvironmentId = :authorityEnvironmentId")
    Optional<ZatcaConfig> findForUpdate(
            @Param("companyId") UUID companyId,
            @Param("authorityEnvironmentId") Short authorityEnvironmentId);

    /**
     * Returns every active ZATCA signing configuration in an environment. Used
     * by the dashboard to evaluate per-company certificate-expiry warnings in a
     * single query (ETA environments have none, so certificates are null there).
     *
     * @param authorityEnvironmentId the active authority environment
     * @return active configurations in the environment
     */
    List<ZatcaConfig> findByAuthorityEnvironmentIdAndIsActiveTrue(
            Short authorityEnvironmentId);
}
