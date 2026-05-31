package com.einvoice.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AssignmentResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("userId") UUID userId,
        @JsonProperty("companyId") UUID companyId,
        @JsonProperty("authorityEnvironmentId") Short authorityEnvironmentId,
        @JsonProperty("transactionType") String transactionType,
        @JsonProperty("roleCode") String roleCode,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("grantedBy") UUID grantedBy,
        @JsonProperty("grantedAt") OffsetDateTime grantedAt
) {}
