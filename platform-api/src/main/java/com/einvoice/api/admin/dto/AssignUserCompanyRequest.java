package com.einvoice.api.admin.dto;

import com.einvoice.core.domain.enums.Role;
import jakarta.validation.constraints.NotNull;

public record AssignUserCompanyRequest(
        @NotNull Long companyId,
        @NotNull Role role
) {}
