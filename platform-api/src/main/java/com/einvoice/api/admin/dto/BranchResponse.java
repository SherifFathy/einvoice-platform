package com.einvoice.api.admin.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO representing a branch belonging to a company.
 */
public record BranchResponse(
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
