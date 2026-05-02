package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new branch under a company.
 */
public record CreateBranchRequest(
        @NotBlank String nameAr,
        @NotBlank String nameEn,
        @NotBlank String branchCode,
        String street,
        String buildingNumber,
        String additionalNumber,
        String city,
        String district,
        String postalCode,
        @Size(min = 2, max = 2) String countryCode,
        String additionalStreet
) {}
