package com.einvoice.api.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record EtaConfigResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("companyId") UUID companyId,
        @JsonProperty("branchId") UUID branchId,
        @JsonProperty("clientId") String clientId,
        @JsonProperty("clientSecret1") String clientSecret1,
        @JsonProperty("clientSecret2") String clientSecret2,
        @JsonProperty("tokenName") String tokenName,
        @JsonProperty("tokenPass") String tokenPass,
        @JsonProperty("submissionUrl") String submissionUrl,
        @JsonProperty("tokenUrl") String tokenUrl,
        @JsonProperty("posSerial") String posSerial,
        @JsonProperty("posOsVersion") String posOsVersion,
        @JsonProperty("posModel") String posModel,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) {}
