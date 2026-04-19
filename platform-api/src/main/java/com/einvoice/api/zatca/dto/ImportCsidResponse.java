package com.einvoice.api.zatca.dto;

import java.time.OffsetDateTime;

public record ImportCsidResponse(
        Long branchId,
        String environment,
        String certificateExpiryDate,
        String status
) {}
