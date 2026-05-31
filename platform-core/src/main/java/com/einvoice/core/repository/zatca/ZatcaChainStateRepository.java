package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaChainState;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA onboarding chain state with pessimistic-lock support. */
@Repository
public interface ZatcaChainStateRepository
        extends JpaRepository<ZatcaChainState, UUID>, JpaSpecificationExecutor<ZatcaChainState> {

    default Optional<ZatcaChainState> findByCompanyAndAuthorityEnvironment(
            UUID companyId, Short authorityEnvironmentId) {
        return findOne(OperationalRepositorySupport.inActiveTenant(companyId, authorityEnvironmentId));
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM ZatcaChainState cs "
            + "WHERE cs.companyId = :companyId "
            + "AND cs.authorityEnvironmentId = :authorityEnvironmentId")
    Optional<ZatcaChainState> findForUpdate(
            @Param("companyId") UUID companyId,
            @Param("authorityEnvironmentId") Short authorityEnvironmentId);
}
