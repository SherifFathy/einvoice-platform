package com.einvoice.api.eta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Javadoc. */
public record EtaItemWriteRequest(
        @JsonProperty("internalCode") @NotBlank @Size(max = 100) String internalCode,
        @JsonProperty("itemType") @NotBlank @Size(max = 10) String itemType,
        @JsonProperty("itemCode") @NotBlank @Size(max = 100) String itemCode,
        @JsonProperty("nameAr") @Size(max = 255) String nameAr,
        @JsonProperty("nameEn") @NotBlank @Size(max = 255) String nameEn,
        @JsonProperty("unitType") @Size(max = 50) String unitType,
        @JsonProperty("unitPrice") @DecimalMin(value = "0", inclusive = true) BigDecimal unitPrice,
        @JsonProperty("taxType") @Size(max = 30) String taxType,
        @JsonProperty("taxSubtype") @Size(max = 30) String taxSubtype,
        @JsonProperty("taxRate") @DecimalMin(value = "0", inclusive = true) BigDecimal taxRate,
        @JsonProperty("isActive") Boolean isActive
) {
    /** Javadoc. */
    public EtaItemWriteRequest {
        if (isActive == null) {
            isActive = true;
        }
    }
}
