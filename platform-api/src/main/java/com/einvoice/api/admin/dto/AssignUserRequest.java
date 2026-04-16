package com.einvoice.api.admin.dto;

import com.einvoice.core.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for assigning a user to a company with a role.
 *
 * @param email the user email address
 * @param role the role to assign
 * @param name the optional display name for new users
 */
public record AssignUserRequest(
        @Email @NotBlank String email,
        @NotNull Role role,
        String name
) {}
