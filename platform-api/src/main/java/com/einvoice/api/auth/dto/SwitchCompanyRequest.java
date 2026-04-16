package com.einvoice.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SwitchCompanyRequest(
        @NotNull Long companyId,
        @NotBlank String password
) {}
