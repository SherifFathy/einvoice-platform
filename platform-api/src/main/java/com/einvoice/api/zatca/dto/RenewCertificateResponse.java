package com.einvoice.api.zatca.dto;

import java.time.OffsetDateTime;

public record RenewCertificateResponse(
        Long branchId,
        String environment,
        OffsetDateTime newCertificateExpiryDate,
        String status
) {}
