package com.einvoice.api.zatca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Write request DTO for creating or updating a ZATCA customer.
 */
public record ZatcaCustomerWriteRequest(
        @JsonProperty("customerType") String customerType,
        @JsonProperty("nameAr") @Size(max = 255) String nameAr,
        @JsonProperty("nameEn") @NotBlank @Size(max = 255) String nameEn,
        @JsonProperty("vatNumber") @Size(max = 100) String vatNumber,
        @JsonProperty("idType") @Size(max = 50) String idType,
        @JsonProperty("idValue") @Size(max = 100) String idValue,
        @JsonProperty("addressData") @Valid Map<String, Object> addressData,
        @JsonProperty("contactEmail") @Email String contactEmail,
        @JsonProperty("contactPhone") @Size(max = 50) String contactPhone,
        @JsonProperty("isActive") Boolean isActive
) {
    /** Compact constructor defaulting isActive to true when null. */
    public ZatcaCustomerWriteRequest {
        if (isActive == null) {
            isActive = true;
        }
    }
}
