package com.einvoice.api.admin.dto;

import java.time.OffsetDateTime;

public record CompanyResponse(
        Long id,
        String nameAr,
        String nameEn,
        String vatNumber,
        String crNumber,
        Boolean isActive,
        OffsetDateTime createdAt
) {}
