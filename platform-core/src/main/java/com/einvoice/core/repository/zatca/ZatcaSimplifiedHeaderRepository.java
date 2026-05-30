package com.einvoice.core.repository.zatca;

import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Repository for ZATCA simplified invoice headers. */
@Repository
public interface ZatcaSimplifiedHeaderRepository
        extends JpaRepository<ZatcaSimplifiedHeader, UUID>,
        JpaSpecificationExecutor<ZatcaSimplifiedHeader> {

    List<ZatcaSimplifiedHeader> findByCompanyIdAndAuthorityEnvironmentIdAndSellerVatNumber(
            UUID companyId, Short authorityEnvironmentId, String sellerVatNumber);

    boolean existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
            UUID companyId, Short authorityEnvironmentId, String invoiceNumber);

    Optional<ZatcaSimplifiedHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
            UUID companyId, Short authorityEnvironmentId, String invoiceNumber);
}
