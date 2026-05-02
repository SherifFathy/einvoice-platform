package com.einvoice.api.company.dto;

import java.time.OffsetDateTime;

public record BranchDetailResponse(
        Long id,
        Long companyId,
        String nameAr,
        String nameEn,
        String branchCode,
        String street,
        String buildingNumber,
        String additionalNumber,
        String city,
        String district,
        String postalCode,
        String countryCode,
        String additionalStreet,
        Boolean isActive,
        OffsetDateTime createdAt
) {}
