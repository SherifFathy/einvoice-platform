package com.einvoice.api.zatca.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record OnboardRequest(
        @NotBlank String environment,
        @Valid CsrDataRequest csrData
) {
    public record CsrDataRequest(
            String commonName,
            String organizationUnit,
            String organization,
            String country,
            String serialNumber,
            String otp
    ) {}
}
