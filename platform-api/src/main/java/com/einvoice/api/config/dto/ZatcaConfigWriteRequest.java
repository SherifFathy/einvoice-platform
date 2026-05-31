package com.einvoice.api.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ZatcaConfigWriteRequest(
        @JsonProperty("privateKey") @NotBlank @Size(max = 10000) String privateKey,
        @JsonProperty("deviceUuid") @NotBlank @Size(max = 500) String deviceUuid,
        @JsonProperty("csr") @NotBlank @Size(max = 10000) String csr,
        @JsonProperty("complianceCertificate") @NotBlank @Size(max = 10000) String complianceCertificate,
        @JsonProperty("complianceApiSecret") @NotBlank @Size(max = 500) String complianceApiSecret,
        @JsonProperty("productionCertificate") @Size(max = 10000) String productionCertificate,
        @JsonProperty("productionApiSecret") @Size(max = 500) String productionApiSecret,
        @JsonProperty("certificateExpiryDate") String certificateExpiryDate,
        @JsonProperty("branchId") UUID branchId
) {}
