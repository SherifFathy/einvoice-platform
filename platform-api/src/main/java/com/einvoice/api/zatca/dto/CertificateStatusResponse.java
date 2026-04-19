package com.einvoice.api.zatca.dto;

import java.time.OffsetDateTime;

public record CertificateStatusResponse(
        Long branchId,
        String environment,
        boolean hasCertificate,
        OffsetDateTime expiryDate,
        long daysUntilExpiry,
        boolean expiryWarning,
        String onboardingStatus
) {}
