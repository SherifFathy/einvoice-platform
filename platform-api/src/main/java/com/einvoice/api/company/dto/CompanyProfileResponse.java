package com.einvoice.api.company.dto;

import java.time.OffsetDateTime;

public record CompanyProfileResponse(
        Long id,
        String nameAr,
        String nameEn,
        String vatNumber,
        String crNumber,
        String street,
        String buildingNumber,
        String city,
        String district,
        String postalCode,
        String countryCode,
        String additionalId,
        Boolean isActive,
        OffsetDateTime createdAt
) {}
