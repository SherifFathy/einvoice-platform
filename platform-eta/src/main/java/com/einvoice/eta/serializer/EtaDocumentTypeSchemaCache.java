package com.einvoice.eta.serializer;

import com.einvoice.core.domain.enums.Environment;
import com.einvoice.eta.auth.EtaTokenManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Cache for ETA document type schemas fetched from the API.
 *
 * <p>Uses EtaTokenManager to obtain bearer tokens for API calls.
 * Falls back gracefully when the token manager has no matching config.</p>
 */
@Service
public class EtaDocumentTypeSchemaCache {

    private static final Logger log = LoggerFactory.getLogger(EtaDocumentTypeSchemaCache.class);

    private static final long CACHE_TTL_SECONDS = 3600;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EtaTokenManager tokenManager;

    private final Map<String, CachedSchema> schemaCache = new ConcurrentHashMap<>();

    /**
     * Creates a new EtaDocumentTypeSchemaCache.
     *
     * @param restClientBuilder the REST client builder
     * @param objectMapper the JSON object mapper
     * @param tokenManager the ETA token manager
     */
    public EtaDocumentTypeSchemaCache(RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper, EtaTokenManager tokenManager) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.tokenManager = tokenManager;
    }

    /**
     * Gets a document type schema, fetching from the API if not cached.
     *
     * @param documentTypeId the document type identifier
     * @param version the schema version
     * @param environment the target environment
     * @return the schema as JSON, or null if not available
     */
    public JsonNode getSchema(String documentTypeId, String version,
            Environment environment) {
        String key = documentTypeId + ":" + version;
        CachedSchema cached = schemaCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.schema;
        }

        String baseUrl = resolveBaseUrl(environment);
        String url = baseUrl + "/documenttypes/" + documentTypeId
                + "/versions/" + version;

        try {
            String response = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                JsonNode schema = objectMapper.readTree(response);
                schemaCache.put(key, new CachedSchema(schema,
                        Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
                return schema;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch ETA document type schema for {}/{}: {}",
                    documentTypeId, version, e.getMessage());
        }

        return null;
    }

    /**
     * Gets a document type schema using a provided bearer token.
     *
     * @param documentTypeId the document type identifier
     * @param version the schema version
     * @param environment the target environment
     * @param bearerToken the bearer token for authentication
     * @return the schema as JSON, or null if not available
     */
    public JsonNode getSchema(String documentTypeId, String version,
            Environment environment, String bearerToken) {
        if (bearerToken != null) {
            return getSchemaWithToken(documentTypeId, version, environment, bearerToken);
        }
        return getSchema(documentTypeId, version, environment);
    }

    private JsonNode getSchemaWithToken(String documentTypeId, String version,
            Environment environment, String bearerToken) {
        String key = documentTypeId + ":" + version;
        CachedSchema cached = schemaCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.schema;
        }

        String baseUrl = resolveBaseUrl(environment);
        String url = baseUrl + "/documenttypes/" + documentTypeId
                + "/versions/" + version;

        try {
            String response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                JsonNode schema = objectMapper.readTree(response);
                schemaCache.put(key, new CachedSchema(schema,
                        Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
                return schema;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch ETA document type schema for {}/{}: {}",
                    documentTypeId, version, e.getMessage());
        }

        return null;
    }

    /**
     * Invalidates the cached schema for a specific document type.
     *
     * @param documentTypeId the document type identifier
     * @param version the schema version
     */
    public void invalidate(String documentTypeId, String version) {
        schemaCache.remove(documentTypeId + ":" + version);
    }

    /**
     * Clears all cached schemas.
     */
    public void clearAll() {
        schemaCache.clear();
    }

    private String resolveBaseUrl(Environment environment) {
        return switch (environment) {
          case ETA_PREPRODUCTION -> "https://api.preprod.invoicing.eta.gov.eg/api/v1";
          case ETA_PRODUCTION -> "https://api.invoicing.eta.gov.eg/api/v1";
          default -> throw new IllegalArgumentException(
                  "Unsupported ETA environment: " + environment);
        };
    }

    private record CachedSchema(JsonNode schema, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
