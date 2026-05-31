package com.einvoice.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("nameEn") String nameEn,
        @JsonProperty("nameAr") String nameAr,
        @JsonProperty("taxNumber") String taxNumber,
        @JsonProperty("crNumber") String crNumber,
        @JsonProperty("logoPath") String logoPath,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
