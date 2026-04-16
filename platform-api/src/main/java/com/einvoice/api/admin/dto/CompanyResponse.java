package com.einvoice.api.admin.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO representing a company with its details.
 */
public record CompanyResponse(
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
