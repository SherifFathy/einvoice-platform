package com.einvoice.eta.auth;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.service.CryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Manages OAuth2 bearer tokens for the Egyptian Tax Authority API.
 *
 * <p>Cache keyed by (branch_id, environment) with proactive refresh
 * 60 seconds before expiry. On 401 the cached token is invalidated so
 * the next call fetches a fresh one.</p>
 */
@Service
public class EtaTokenManager {

    private static final Logger log = LoggerFactory.getLogger(EtaTokenManager.class);

    private static final long REFRESH_BUFFER_SECONDS = 60;
    private static final String TOKEN_PATH = "/connect/token";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;

    private final ConcurrentHashMap<String, CachedToken> tokenCache =
            new ConcurrentHashMap<>();

    /**
     * Creates a new EtaTokenManager.
     *
     * @param restClientBuilder the REST client builder
     * @param objectMapper the JSON object mapper
     * @param cryptoService the crypto service for credential decryption
     */
    public EtaTokenManager(RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper, CryptoService cryptoService) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(15));
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
    }

    /**
     * Gets a valid token, refreshing if necessary.
     *
     * @param config the authority configuration
     * @return a valid access token
     */
    public String getToken(AuthorityConfig config) {
        String key = cacheKey(config.getBranch().getId(), config.getEnvironment());
        CachedToken cached = tokenCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.accessToken;
        }
        return refreshToken(config);
    }

    /**
     * Forces a token refresh for the given configuration.
     *
     * @param config the authority configuration
     * @return a fresh access token
     */
    public String refreshToken(AuthorityConfig config) {
        String key = cacheKey(config.getBranch().getId(), config.getEnvironment());
        String clientId = resolveClientId(config);
        String clientSecret = resolveClientSecret(config);
        String baseUrl = resolveBaseUrl(config.getEnvironment());

        String encodedClientId = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        String encodedSecret = URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        String body = "grant_type=client_credentials"
                + "&client_id=" + encodedClientId
                + "&client_secret=" + encodedSecret;

        String response;
        try {
            response = restClient.post()
                    .uri(baseUrl + TOKEN_PATH)
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.error("ETA token request failed with status {}: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new EtaTokenException(
                    "Failed to obtain ETA token: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("ETA token request transport error", e);
            throw new EtaTokenException(
                    "Failed to connect to ETA token endpoint", e);
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            String accessToken = root.path("access_token").asText();
            int expiresIn = root.path("expires_in").asInt(3600);
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            tokenCache.put(key, new CachedToken(accessToken, expiresAt));
            log.debug("ETA token refreshed for {}, expires in {}s", key, expiresIn);
            return accessToken;
        } catch (Exception e) {
            throw new EtaTokenException(
                    "Failed to parse ETA token response", e);
        }
    }

    /**
     * Invalidates the cached token for the given branch and environment.
     *
     * @param branchId the branch identifier
     * @param environment the target environment
     */
    public void invalidateToken(Long branchId, Environment environment) {
        String key = cacheKey(branchId, environment);
        tokenCache.remove(key);
        log.debug("ETA token invalidated for {}", key);
    }

    /**
     * Gets a token, refreshing if the cached one is expired or missing.
     * If the cached token appears valid but the caller receives a 401,
     * the caller should call {@link #invalidateToken} then this method again.
     *
     * @param config the authority configuration
     * @return a valid access token
     */
    public String getTokenWithRetry(AuthorityConfig config) {
        String key = cacheKey(config.getBranch().getId(), config.getEnvironment());
        CachedToken cached = tokenCache.get(key);
        if (cached == null || cached.isExpired()) {
            return refreshToken(config);
        }
        return cached.accessToken;
    }

    private String cacheKey(Long branchId, Environment environment) {
        return branchId + ":" + environment.name();
    }

    private String resolveClientId(AuthorityConfig config) {
        if (config.getCsidEncrypted() == null) {
            throw new IllegalStateException("ETA client ID not configured");
        }
        byte[] decrypted = cryptoService.decrypt(config.getCsidEncrypted());
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String resolveClientSecret(AuthorityConfig config) {
        if (config.getCredentialsEncrypted() == null) {
            throw new IllegalStateException("ETA client secret not configured");
        }
        byte[] decrypted = cryptoService.decrypt(config.getCredentialsEncrypted());
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String resolveBaseUrl(Environment environment) {
        return switch (environment) {
          case ETA_PREPRODUCTION -> "https://id.preprod.eta.gov.eg";
          case ETA_PRODUCTION -> "https://id.eta.gov.eg";
          default -> throw new IllegalArgumentException(
                  "Unsupported ETA environment: " + environment);
        };
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(
                    expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS));
        }
    }

    /**
     * Exception thrown when ETA token operations fail.
     */
    public static class EtaTokenException extends RuntimeException {
        /**
         * Creates a new EtaTokenException.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public EtaTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
