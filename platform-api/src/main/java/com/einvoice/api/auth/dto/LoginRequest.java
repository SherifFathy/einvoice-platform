package com.einvoice.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank @Pattern(regexp = "ETA|ZATCA") String authority,
        @NotBlank @Pattern(regexp = "PRODUCTION|PREPROD|SIMULATION|SANDBOX") String environment,
        UUID companyId
) {}
