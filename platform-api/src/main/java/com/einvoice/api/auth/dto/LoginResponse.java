package com.einvoice.api.auth.dto;

import java.util.List;
import java.util.Map;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {

    public record UserInfo(
            Long id,
            String name,
            String email,
            Long activeCompanyId,
            String role,
            List<String> permittedEnvironments,
            List<Map<String, Object>> availableCompanies,
            String activeEnvironment
    ) {}
}
