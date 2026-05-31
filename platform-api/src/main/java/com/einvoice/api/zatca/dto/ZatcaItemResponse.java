package com.einvoice.api.zatca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Javadoc. */
public record ZatcaItemResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("companyId") UUID companyId,
        @JsonProperty("internalCode") String internalCode,
        @JsonProperty("itemCode") String itemCode,
        @JsonProperty("nameAr") String nameAr,
        @JsonProperty("nameEn") String nameEn,
        @JsonProperty("unitType") String unitType,
        @JsonProperty("unitPrice") BigDecimal unitPrice,
        @JsonProperty("vatCategory") String vatCategory,
        @JsonProperty("vatRate") BigDecimal vatRate,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
