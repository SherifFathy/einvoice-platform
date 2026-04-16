package com.einvoice.api.customer.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for creating or updating a customer. */
public record CustomerRequest(
        String nameAr,
        @NotBlank String nameEn,
        String vatNumber,
        String idType,
        String idValue,
        String street,
        String buildingNumber,
        String city,
        String district,
        String postalCode,
        String countryCode,
        @NotBlank String customerType,
        String contactEmail,
        String contactPhone
) {}
