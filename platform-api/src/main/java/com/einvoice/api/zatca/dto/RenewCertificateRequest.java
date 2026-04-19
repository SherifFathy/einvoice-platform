package com.einvoice.api.zatca.dto;

import jakarta.validation.constraints.NotBlank;

public record RenewCertificateRequest(
        @NotBlank String environment
) {}
