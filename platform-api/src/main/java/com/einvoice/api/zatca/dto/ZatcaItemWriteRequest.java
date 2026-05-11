package com.einvoice.api.zatca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Javadoc. */
public record ZatcaItemWriteRequest(
        @JsonProperty("internalCode") @NotBlank @Size(max = 100) String internalCode,
        @JsonProperty("itemCode") @Size(max = 100) String itemCode,
        @JsonProperty("nameAr") @Size(max = 255) String nameAr,
        @JsonProperty("nameEn") @NotBlank @Size(max = 255) String nameEn,
        @JsonProperty("unitType") @Size(max = 50) String unitType,
        @JsonProperty("unitPrice") @DecimalMin(value = "0", inclusive = true) BigDecimal unitPrice,
        @JsonProperty("vatCategory") @NotBlank @Size(max = 10) String vatCategory,
        @JsonProperty("vatRate") @DecimalMin(value = "0", inclusive = true) BigDecimal vatRate,
        @JsonProperty("isActive") Boolean isActive
) {
    /** Javadoc. */
    public ZatcaItemWriteRequest {
        if (isActive == null) {
            isActive = true;
        }
    }
}
