package com.einvoice.api.branch.dto;

import java.util.UUID;

/** Branch option visible to the active session. */
public record BranchLookupResponse(
        UUID id,
        UUID companyId,
        String companyNameEn,
        String nameEn,
        String nameAr,
        String branchCode,
        Boolean isActive) {
}
