package com.einvoice.api.fixtures;

import com.einvoice.core.domain.eta.EtaCustomer;
import com.einvoice.core.repository.eta.EtaCustomerRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Negative-control fixture — uses only Specification-based queries
 * routed through OperationalRepositorySupport. Used by
 * {@code Wave6CompoundFilterEnforcementTest} to prove the scanner
 * does not produce false positives.
 */
@Service
public class CompliantCallFixture {

    private final EtaCustomerRepository repo;

    public CompliantCallFixture(EtaCustomerRepository repo) {
        this.repo = repo;
    }

    /**
     * Loads an ETA customer using compliant Specification-based tenant filtering.
     *
     * @return an optional containing the customer if found
     */
    public Optional<EtaCustomer> loadInTenant() {
        Specification<EtaCustomer> spec = Specification
                .where(OperationalRepositorySupport.<EtaCustomer>inActiveTenant());
        return repo.findOne(spec);
    }
}
