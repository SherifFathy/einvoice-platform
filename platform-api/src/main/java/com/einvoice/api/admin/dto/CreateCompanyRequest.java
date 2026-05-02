package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
        @NotBlank String nameAr,
        @NotBlank String nameEn,
        @NotBlank String vatNumber,
        String crNumber
) {}
