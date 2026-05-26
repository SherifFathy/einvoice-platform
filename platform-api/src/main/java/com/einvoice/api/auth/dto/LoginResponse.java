package com.einvoice.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("tokenType") String tokenType,
        @JsonProperty("expiresInSeconds") long expiresInSeconds,
        @JsonProperty("mode") String mode
) {}
