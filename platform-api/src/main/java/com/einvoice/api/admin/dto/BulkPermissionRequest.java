package com.einvoice.api.admin.dto;

import com.einvoice.core.domain.enums.Permission;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BulkPermissionRequest(
        @NotNull Long companyId,
        @NotNull Long lovContextId,
        List<Permission> permissions
) {}
