package com.einvoice.api.company.dto;

public record UpdateCompanyRequest(
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
        String additionalId
) {}
