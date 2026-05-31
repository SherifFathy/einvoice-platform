package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaStandardAllowance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA standard invoice header-level allowances. */
@Repository
public interface ZatcaStandardAllowanceRepository
        extends JpaRepository<ZatcaStandardAllowance, UUID> {
}
