package com.einvoice.api.admin.dto;

public record UserCompanyAssignment(
        Long companyId,
        String companyName,
        String role,
        Boolean isActive
) {}
