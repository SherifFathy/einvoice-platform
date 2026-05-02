package com.einvoice.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        @NotBlank String authority,
        @NotBlank String docType,
        @NotBlank String subEnvironment
) {}
