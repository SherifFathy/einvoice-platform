package com.einvoice.api.company.dto;

import java.time.OffsetDateTime;

public record CompanyProfileResponse(
        Long id,
        String nameAr,
        String nameEn,
        String vatNumber,
        String crNumber,
        Boolean isActive,
        OffsetDateTime createdAt
) {}
