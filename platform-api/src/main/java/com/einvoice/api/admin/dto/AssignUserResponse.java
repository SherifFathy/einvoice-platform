package com.einvoice.api.admin.dto;

/**
 * Response DTO for a user-to-company role assignment result.
 */
public record AssignUserResponse(
        Long userId,
        Long companyId,
        String role,
        boolean userCreated
) {}
