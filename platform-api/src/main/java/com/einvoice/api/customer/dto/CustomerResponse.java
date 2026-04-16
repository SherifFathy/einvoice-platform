package com.einvoice.api.customer.dto;

import java.time.OffsetDateTime;

/** Response body for customer data. */
public record CustomerResponse(
        Long id,
        String nameAr,
        String nameEn,
        String vatNumber,
        String idType,
        String idValue,
        String street,
        String buildingNumber,
        String city,
        String district,
        String postalCode,
        String countryCode,
        String customerType,
        String contactEmail,
        String contactPhone,
        Boolean isActive,
        OffsetDateTime createdAt
) {}
