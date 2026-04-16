package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new company.
 */
public record CreateCompanyRequest(
        @NotBlank String nameAr,
        @NotBlank String nameEn,
        @NotBlank String vatNumber,
        String crNumber,
        String street,
        String buildingNumber,
        String city,
        String district,
        String postalCode,
        String countryCode,
        String additionalId
) {}
