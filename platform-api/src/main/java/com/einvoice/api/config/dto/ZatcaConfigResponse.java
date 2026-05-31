package com.einvoice.api.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ZatcaConfigResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("companyId") UUID companyId,
        @JsonProperty("privateKey") String privateKey,
        @JsonProperty("deviceUuid") String deviceUuid,
        @JsonProperty("csr") String csr,
        @JsonProperty("complianceCertificate") String complianceCertificate,
        @JsonProperty("complianceApiSecret") String complianceApiSecret,
        @JsonProperty("productionCertificate") String productionCertificate,
        @JsonProperty("productionApiSecret") String productionApiSecret,
        @JsonProperty("certificateExpiryDate") LocalDate certificateExpiryDate,
        @JsonProperty("chainStateInitialized") boolean chainStateInitialized,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
