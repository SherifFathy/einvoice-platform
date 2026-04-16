package com.einvoice.api.admin.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO representing a branch belonging to a company.
 */
public record BranchResponse(
        Long id,
        Long companyId,
        String nameAr,
        String nameEn,
        String branchCode,
        Boolean isActive,
        OffsetDateTime createdAt
) {}
