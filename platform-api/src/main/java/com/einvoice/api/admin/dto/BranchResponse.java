package com.einvoice.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BranchResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("companyId") UUID companyId,
        @JsonProperty("nameEn") String nameEn,
        @JsonProperty("nameAr") String nameAr,
        @JsonProperty("branchCode") String branchCode,
        @JsonProperty("addressLine1") String addressLine1,
        @JsonProperty("addressLine2") String addressLine2,
        @JsonProperty("city") String city,
        @JsonProperty("region") String region,
        @JsonProperty("postalCode") String postalCode,
        @JsonProperty("country") String country,
        @JsonProperty("buildingNumber") String buildingNumber,
        @JsonProperty("additionalNo") String additionalNo,
        @JsonProperty("taxpayerActivityCode") String taxpayerActivityCode,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
