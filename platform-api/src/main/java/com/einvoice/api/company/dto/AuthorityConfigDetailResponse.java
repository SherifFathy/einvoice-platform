package com.einvoice.api.company.dto;

import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;

public record AuthorityConfigDetailResponse(
        Long id,
        Long branchId,
        Authority authority,
        Environment environment,
        boolean hasCredentials,
        boolean hasCertificate,
        String certificateExpiryDate,
        Long invoiceCounter,
        String invoicePrefix,
        Long invoiceStartingNumber,
        String invoiceResetPolicy,
        Boolean isActive
) {}
