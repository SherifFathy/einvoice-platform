package com.einvoice.api.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchRequest(
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
