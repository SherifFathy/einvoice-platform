package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaStandardLineAllowance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA standard invoice line-level allowances. */
@Repository
public interface ZatcaStandardLineAllowanceRepository
        extends JpaRepository<ZatcaStandardLineAllowance, UUID> {
}
