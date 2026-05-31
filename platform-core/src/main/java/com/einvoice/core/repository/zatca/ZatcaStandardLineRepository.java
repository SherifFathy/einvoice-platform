package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaStandardLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA standard invoice lines. */
@Repository
public interface ZatcaStandardLineRepository
        extends JpaRepository<ZatcaStandardLine, UUID> {
}
