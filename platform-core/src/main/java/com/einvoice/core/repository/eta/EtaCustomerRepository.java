package com.einvoice.core.repository.eta;

import com.einvoice.core.domain.eta.EtaCustomer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface EtaCustomerRepository
        extends JpaRepository<EtaCustomer, UUID>, JpaSpecificationExecutor<EtaCustomer> {
}
