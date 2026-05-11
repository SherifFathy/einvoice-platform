package com.einvoice.api.eta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record EtaCustomerResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("companyId") UUID companyId,
        @JsonProperty("customerType") String customerType,
        @JsonProperty("nameAr") String nameAr,
        @JsonProperty("nameEn") String nameEn,
        @JsonProperty("taxNumber") String taxNumber,
        @JsonProperty("idType") String idType,
        @JsonProperty("idValue") String idValue,
        @JsonProperty("addressData") Map<String, Object> addressData,
        @JsonProperty("contactEmail") String contactEmail,
        @JsonProperty("contactPhone") String contactPhone,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
