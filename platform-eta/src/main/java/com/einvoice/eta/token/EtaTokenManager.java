package com.einvoice.eta.token;

import com.einvoice.eta.client.EtaHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Manages ETA OAuth access tokens with expiry-aware caching.
 *
 * <p>Cache TTL is derived dynamically from each token's
 * {@code expires_in} claim minus a 60-second safety margin,
 * as required by Research Decision 6.</p>
 */
@Component
public class EtaTokenManager {

    private static final Duration SAFETY_MARGIN =
            Duration.ofSeconds(60);

    private final Cache<TokenKey, TokenEntry> cache;
    private final EtaHttpClient httpClient;

    /**
     * Constructs an EtaTokenManager with the given HTTP client.
     *
     * @param httpClient the ETA HTTP client used to request tokens
     */
    public EtaTokenManager(EtaHttpClient httpClient) {
        this.httpClient = httpClient;
        this.cache = Caffeine.newBuilder()
                .expireAfter(new TokenExpiry())
                .maximumSize(1000)
                .build();
    }

    /**
     * Retrieves a cached or fresh OAuth token for the given company
     * and environment.
     *
     * @param companyId the company identifier
     * @param authorityEnvironmentId the ETA environment identifier
     * @param clientId the OAuth client identifier
     * @param clientSecret the OAuth client secret
     * @param tokenUrl the token endpoint URL
     * @return the OAuth access token
     */
    public String getToken(UUID companyId, Short authorityEnvironmentId,
            String clientId, String clientSecret, String tokenUrl) {
        TokenKey key = new TokenKey(companyId, authorityEnvironmentId);
        TokenEntry entry = cache.get(key, k -> {
            String rawResponse = httpClient.requestTokenRaw(
                    clientId, clientSecret, tokenUrl);
            return parseTokenEntry(rawResponse);
        });
        return entry.accessToken;
    }

    /**
     * Invalidates the cached token for the given company and environment.
     *
     * @param companyId the company identifier
     * @param authorityEnvironmentId the ETA environment identifier
     */
    public void invalidate(UUID companyId,
            Short authorityEnvironmentId) {
        cache.invalidate(new TokenKey(companyId,
                authorityEnvironmentId));
    }

    private TokenEntry parseTokenEntry(String rawResponse) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode node = mapper.readTree(rawResponse);
            String accessToken = node.get("access_token").asText();
            long expiresIn = node.has("expires_in")
                    ? node.get("expires_in").asLong()
                    : 3600L;
            long ttlSeconds = Math.max(expiresIn
                    - SAFETY_MARGIN.getSeconds(), 60);
            return new TokenEntry(accessToken, ttlSeconds);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse token response", e);
        }
    }

    private record TokenKey(UUID companyId,
            Short authorityEnvironmentId) {}

    private record TokenEntry(String accessToken,
            long ttlSeconds) {}

    private static class TokenExpiry
            implements Expiry<TokenKey, TokenEntry> {
        @Override
        public long expireAfterCreate(TokenKey key, TokenEntry value,
                long currentTime) {
            return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
        }

        @Override
        public long expireAfterUpdate(TokenKey key, TokenEntry value,
                long currentTime, long currentLoadDuration) {
            return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
        }

        @Override
        public long expireAfterRead(TokenKey key, TokenEntry value,
                long currentTime, long currentLoadDuration) {
            return Long.MAX_VALUE;
        }
    }
}
