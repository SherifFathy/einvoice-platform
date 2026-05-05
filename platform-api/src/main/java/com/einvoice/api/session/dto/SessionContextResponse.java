package com.einvoice.api.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SessionContextResponse(
        UUID userId,
        boolean isSuperUser,
        String mode,
        LoginContext loginContext,
        List<CompanyContext> companies
) {
    public record LoginContext(
            String authority,
            String environment,
            @JsonProperty("authorityEnvironmentId") Short authorityEnvironmentId
    ) {}

    public record CompanyContext(
            UUID companyId,
            String companyNameEn,
            String companyNameAr,
            boolean isActive,
            Map<String, ModulePermissions> modules
    ) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ModulePermissions(
            boolean visible,
            Permissions permissions
    ) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Permissions(
            boolean view,
            boolean create,
            boolean edit,
            boolean delete,
            boolean cancel,
            boolean transfer,
            boolean refresh,
            boolean submit
    ) {}
}
