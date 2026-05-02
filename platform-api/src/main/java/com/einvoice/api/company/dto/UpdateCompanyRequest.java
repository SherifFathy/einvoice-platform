package com.einvoice.api.company.dto;

public record UpdateCompanyRequest(
        String nameAr,
        String nameEn,
        String vatNumber,
        String crNumber
) {}
