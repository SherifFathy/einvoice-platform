package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaChainState;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface ZatcaChainStateRepository
        extends JpaRepository<ZatcaChainState, UUID>, JpaSpecificationExecutor<ZatcaChainState> {

    default Optional<ZatcaChainState> findByCompanyAndAuthorityEnvironment(
            UUID companyId, Short authorityEnvironmentId) {
        return findOne(OperationalRepositorySupport.inActiveTenant(companyId, authorityEnvironmentId));
    }
}
