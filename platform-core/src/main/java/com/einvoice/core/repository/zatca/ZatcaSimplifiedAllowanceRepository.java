package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaSimplifiedAllowance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA simplified invoice header-level allowances. */
@Repository
public interface ZatcaSimplifiedAllowanceRepository
        extends JpaRepository<ZatcaSimplifiedAllowance, UUID> {
}
