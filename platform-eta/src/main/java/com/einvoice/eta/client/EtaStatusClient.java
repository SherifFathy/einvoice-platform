package com.einvoice.eta.client;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.eta.auth.EtaTokenManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * REST client for querying ETA document status and details.
 */
@Service
public class EtaStatusClient {

    private static final Logger log = LoggerFactory.getLogger(EtaStatusClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EtaTokenManager tokenManager;

    /**
     * Creates a new EtaStatusClient.
     *
     * @param restClientBuilder the REST client builder
     * @param objectMapper the JSON object mapper
     * @param tokenManager the ETA token manager
     */
    public EtaStatusClient(RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper, EtaTokenManager tokenManager) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.tokenManager = tokenManager;
    }

    /**
     * Gets document details from ETA.
     *
     * @param documentId the document identifier
     * @param config the authority configuration
     * @return the document details as JSON, or empty if not found
     */
    public Optional<JsonNode> getDocumentDetails(String documentId,
            AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config.getEnvironment());
        String token = tokenManager.getToken(config);

        try {
            String response = restClient.get()
                    .uri(baseUrl + "/documents/" + documentId + "/details")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (response != null && !response.isBlank()) {
                return Optional.of(objectMapper.readTree(response));
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("ETA document {} not found", documentId);
                return Optional.empty();
            }
            log.warn("ETA document details request failed: {}", e.getStatusCode());
            if (e.getStatusCode().value() == 401) {
                tokenManager.invalidateToken(
                        config.getBranch().getId(), config.getEnvironment());
            }
        } catch (Exception e) {
            log.error("ETA document details request error", e);
        }

        return Optional.empty();
    }

    /**
     * Gets recent documents from ETA.
     *
     * @param config the authority configuration
     * @param pageSize the number of documents to retrieve
     * @return the recent documents as JSON, or empty on failure
     */
    public Optional<JsonNode> getRecentDocuments(AuthorityConfig config,
            int pageSize) {
        String baseUrl = resolveBaseUrl(config.getEnvironment());
        String token = tokenManager.getToken(config);

        try {
            String response = restClient.get()
                    .uri(baseUrl + "/documents/recent?pageNo=0&pageSize=" + pageSize)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (response != null && !response.isBlank()) {
                return Optional.of(objectMapper.readTree(response));
            }
        } catch (Exception e) {
            log.warn("ETA recent documents request failed: {}", e.getMessage());
        }

        return Optional.empty();
    }

    private String resolveBaseUrl(Environment environment) {
        return switch (environment) {
          case ETA_PREPRODUCTION -> "https://api.preprod.invoicing.eta.gov.eg/api/v1";
          case ETA_PRODUCTION -> "https://api.invoicing.eta.gov.eg/api/v1";
          default -> throw new IllegalArgumentException(
                  "Unsupported ETA environment: " + environment);
        };
    }
}
