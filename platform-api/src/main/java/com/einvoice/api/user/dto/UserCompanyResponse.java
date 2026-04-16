package com.einvoice.api.user.dto;

import java.util.List;

public record UserCompanyResponse(
        Long id,
        String name,
        String email,
        String role,
        Boolean isActive,
        List<String> permittedEnvironments
) {}
