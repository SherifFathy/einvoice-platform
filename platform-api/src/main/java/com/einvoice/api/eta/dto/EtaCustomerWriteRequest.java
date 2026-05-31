package com.einvoice.api.eta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Write request DTO for creating or updating an ETA customer.
 */
public record EtaCustomerWriteRequest(
        @JsonProperty("customerType") String customerType,
        @JsonProperty("nameAr") @Size(max = 255) String nameAr,
        @JsonProperty("nameEn") @NotBlank @Size(max = 255) String nameEn,
        @JsonProperty("taxNumber") @Size(max = 100) String taxNumber,
        @JsonProperty("idType") @Size(max = 50) String idType,
        @JsonProperty("idValue") @Size(max = 100) String idValue,
        @JsonProperty("addressData") @Valid Map<String, Object> addressData,
        @JsonProperty("contactEmail") @Email String contactEmail,
        @JsonProperty("contactPhone") @Size(max = 50) String contactPhone,
        @JsonProperty("isActive") Boolean isActive
) {
    /** Compact constructor defaulting isActive to true when null. */
    public EtaCustomerWriteRequest {
        if (isActive == null) {
            isActive = true;
        }
    }
}
