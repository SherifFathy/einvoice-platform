package com.einvoice.api.admin.dto;

import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceResetPolicy;

/**
 * Response DTO representing an authority configuration for a branch.
 */
public record AuthorityConfigResponse(
        Long id,
        Long branchId,
        Authority authority,
        Environment environment,
        boolean hasCredentials,
        boolean hasCertificate,
        boolean hasCsid,
        boolean hasPrivateKey,
        boolean hasTokenData,
        String certificateExpiryDate,
        Long invoiceCounter,
        String invoicePrefix,
        Long invoiceStartingNumber,
        InvoiceResetPolicy invoiceResetPolicy,
        String enabledDocumentTypes,
        Boolean isActive
) {}
