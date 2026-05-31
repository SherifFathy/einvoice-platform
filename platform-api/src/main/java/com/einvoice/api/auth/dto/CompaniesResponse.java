package com.einvoice.api.auth.dto;

import java.util.List;
import java.util.UUID;

public record CompaniesResponse(
        Boolean isSuperUser,
        List<CompanyEntry> companies
) {
    public record CompanyEntry(
            UUID companyId,
            String nameEn,
            String nameAr,
            String taxNumber,
            Boolean isActive
    ) {}
}
