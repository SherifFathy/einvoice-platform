package com.einvoice.api.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String name,
        String email,
        Boolean isActive,
        Boolean isSuperUser,
        OffsetDateTime createdAt,
        List<UserCompanyAssignment> companies
) {}
