package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaStandardTaxSubtotal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA standard invoice tax subtotals. */
@Repository
public interface ZatcaStandardTaxSubtotalRepository
        extends JpaRepository<ZatcaStandardTaxSubtotal, UUID> {
}
