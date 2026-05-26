package com.einvoice.core.repository.eta;

import com.einvoice.core.domain.eta.EtaReceiptHeader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Javadoc. */
public interface EtaReceiptHeaderRepository
        extends JpaRepository<EtaReceiptHeader, UUID>,
        JpaSpecificationExecutor<EtaReceiptHeader> {

    Optional<EtaReceiptHeader> findByCompanyIdAndAuthorityEnvironmentIdAndReceiptNumber(
            UUID companyId, Short authorityEnvironmentId, String receiptNumber);
}
