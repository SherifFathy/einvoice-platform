package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaSimplifiedLineAllowance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA simplified invoice line-level allowances. */
@Repository
public interface ZatcaSimplifiedLineAllowanceRepository
        extends JpaRepository<ZatcaSimplifiedLineAllowance, UUID> {
}
