package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record AssignmentCreateRequest(
        @NotNull UUID companyId,
        @NotNull Short authorityEnvironmentId,
        @NotNull @Pattern(regexp = "INVOICE|RECEIPT|STANDARD|SIMPLIFIED|CUSTOMERS|ITEMS|CONFIG") String transactionType,
        @NotNull @Pattern(regexp = "COMPANY_ADMIN|ACCOUNTANT|VIEWER") String roleCode
) {}
