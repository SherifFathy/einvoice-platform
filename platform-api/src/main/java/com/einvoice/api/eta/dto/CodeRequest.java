package com.einvoice.api.eta.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for registering or updating an ETA item code.
 */
public record CodeRequest(
        @NotBlank String itemCode,
        @NotBlank String codeType,
        String description
) {}
