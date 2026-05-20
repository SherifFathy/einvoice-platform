package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA standard invoice headers. */
@Repository
public interface ZatcaStandardHeaderRepository
        extends JpaRepository<ZatcaStandardHeader, UUID>,
        JpaSpecificationExecutor<ZatcaStandardHeader> {
}
