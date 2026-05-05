package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyCreateRequest(
        @NotBlank @Size(max = 255) String nameEn,
        @NotBlank @Size(max = 255) String nameAr,
        @NotBlank @Size(max = 100) String taxNumber,
        @Size(max = 100) String crNumber
) {}
