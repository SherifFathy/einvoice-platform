package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String newPassword
) {}
