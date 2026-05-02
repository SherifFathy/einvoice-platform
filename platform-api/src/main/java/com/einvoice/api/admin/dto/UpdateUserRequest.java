package com.einvoice.api.admin.dto;

public record UpdateUserRequest(
        String name,
        String email
) {}
