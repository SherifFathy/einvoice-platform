package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaSimplifiedLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA simplified invoice lines. */
@Repository
public interface ZatcaSimplifiedLineRepository
        extends JpaRepository<ZatcaSimplifiedLine, UUID> {
}
