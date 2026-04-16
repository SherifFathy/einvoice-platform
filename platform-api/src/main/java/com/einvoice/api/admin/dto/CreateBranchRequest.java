package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new branch under a company.
 */
public record CreateBranchRequest(
        @NotBlank String nameAr,
        @NotBlank String nameEn,
        @NotBlank String branchCode
) {}
