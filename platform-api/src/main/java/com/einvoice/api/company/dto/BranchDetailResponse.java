package com.einvoice.api.company.dto;

import java.time.OffsetDateTime;

public record BranchDetailResponse(
        Long id,
        Long companyId,
        String nameAr,
        String nameEn,
        String branchCode,
        Boolean isActive,
        OffsetDateTime createdAt
) {}
