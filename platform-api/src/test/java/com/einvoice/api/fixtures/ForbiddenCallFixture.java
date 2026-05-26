package com.einvoice.api.fixtures;

import com.einvoice.core.domain.eta.EtaCustomer;
import com.einvoice.core.repository.eta.EtaCustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Positive-control fixture — intentionally calls a forbidden
 * repository method. Used by {@code Wave6CompoundFilterEnforcementTest}
 * to prove the ASM bytecode scanner detects violations.
 */
@Service
public class ForbiddenCallFixture {

    private final EtaCustomerRepository repo;

    public ForbiddenCallFixture(EtaCustomerRepository repo) {
        this.repo = repo;
    }

    public Optional<EtaCustomer> loadById(UUID id) {
        return repo.findById(id);
    }
}
