package com.einvoice.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CompaniesRequest(
        @NotBlank @Pattern(regexp = "ETA|ZATCA") String authority,
        @NotBlank @Pattern(regexp = "PRODUCTION|PREPROD|SIMULATION|SANDBOX") String environment,
        @NotBlank @Email String email
) {}
