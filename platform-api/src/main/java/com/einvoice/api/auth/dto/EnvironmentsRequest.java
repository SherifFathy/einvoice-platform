package com.einvoice.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EnvironmentsRequest(
        @NotBlank @Pattern(regexp = "ETA|ZATCA") String authority
) {}
