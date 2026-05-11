package com.einvoice.core.repository.config;

import com.einvoice.core.domain.config.ZatcaConfig;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface ZatcaConfigRepository
        extends JpaRepository<ZatcaConfig, UUID>, JpaSpecificationExecutor<ZatcaConfig> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ZatcaConfig e "
            + "WHERE e.companyId = :companyId "
            + "AND e.authorityEnvironmentId = :authorityEnvironmentId")
    Optional<ZatcaConfig> findForUpdate(
            @Param("companyId") UUID companyId,
            @Param("authorityEnvironmentId") Short authorityEnvironmentId);
}
