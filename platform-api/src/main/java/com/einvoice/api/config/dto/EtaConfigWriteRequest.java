package com.einvoice.api.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EtaConfigWriteRequest(
        @JsonProperty("clientId") @NotBlank @Size(max = 500) String clientId,
        @JsonProperty("clientSecret1") @NotBlank @Size(max = 500) String clientSecret1,
        @JsonProperty("clientSecret2") @NotBlank @Size(max = 500) String clientSecret2,
        @JsonProperty("tokenName") @Size(max = 500) String tokenName,
        @JsonProperty("tokenPass") @Size(max = 500) String tokenPass,
        @JsonProperty("submissionUrl") @NotBlank @Size(max = 2000) String submissionUrl,
        @JsonProperty("tokenUrl") @NotBlank @Size(max = 2000) String tokenUrl,
        @JsonProperty("posSerial") @Size(max = 200) String posSerial,
        @JsonProperty("posOsVersion") @Size(max = 200) String posOsVersion,
        @JsonProperty("posModel") @Size(max = 200) String posModel,
        @JsonProperty("branchId") UUID branchId
) {}
