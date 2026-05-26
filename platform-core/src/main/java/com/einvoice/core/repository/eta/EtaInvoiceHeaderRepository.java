package com.einvoice.core.repository.eta;

import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Javadoc. */
public interface EtaInvoiceHeaderRepository
        extends JpaRepository<EtaInvoiceHeader, UUID>,
        JpaSpecificationExecutor<EtaInvoiceHeader> {

    Optional<EtaInvoiceHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
            UUID companyId, Short authorityEnvironmentId, String invoiceNumber);
}
