package com.einvoice.api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        String password,
        Boolean isSuperUser
) {}
