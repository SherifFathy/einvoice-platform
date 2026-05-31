package com.einvoice.api.eta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Javadoc. */
public record EtaItemResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("companyId") UUID companyId,
        @JsonProperty("internalCode") String internalCode,
        @JsonProperty("itemType") String itemType,
        @JsonProperty("itemCode") String itemCode,
        @JsonProperty("nameAr") String nameAr,
        @JsonProperty("nameEn") String nameEn,
        @JsonProperty("unitType") String unitType,
        @JsonProperty("unitPrice") BigDecimal unitPrice,
        @JsonProperty("taxType") String taxType,
        @JsonProperty("taxSubtype") String taxSubtype,
        @JsonProperty("taxRate") BigDecimal taxRate,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
