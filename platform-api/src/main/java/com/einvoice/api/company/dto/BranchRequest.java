package com.einvoice.api.company.dto;

import jakarta.validation.constraints.NotBlank;

public record BranchRequest(
        @NotBlank String nameAr,
        @NotBlank String nameEn,
        @NotBlank String branchCode
) {}
