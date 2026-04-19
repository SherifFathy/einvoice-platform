package com.einvoice.eta.client;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.service.SubmissionResultDto;
import com.einvoice.eta.auth.EtaTokenManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * REST client for submitting invoices to the ETA portal.
 */
@Service
public class EtaSubmissionClient {

    private static final Logger log = LoggerFactory.getLogger(EtaSubmissionClient.class);

    private static final String SUBMISSION_PATH = "/documentsubmissions";
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final long RATE_LIMIT_BACKOFF_FIRST = 2000;
    private static final long RATE_LIMIT_BACKOFF_SECOND = 4000;
    private static final long RATE_LIMIT_BACKOFF_THIRD = 8000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EtaTokenManager tokenManager;

    /**
     * Creates a new EtaSubmissionClient.
     *
     * @param restClientBuilder the REST client builder
     * @param objectMapper the JSON object mapper
     * @param tokenManager the ETA token manager
     */
    public EtaSubmissionClient(RestClient.Builder restClientBuilder,
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
     * Submits an invoice payload to ETA with a CAdES signature.
     *
     * @param jsonPayload the JSON invoice payload
     * @param signatureBase64 the Base64-encoded CAdES signature
     * @param config the authority configuration
     * @return the submission result
     */
    public SubmissionResultDto submit(String jsonPayload, String signatureBase64,
            AuthorityConfig config) {
        return doSubmit(jsonPayload, signatureBase64, config, false, 0);
    }

    private SubmissionResultDto doSubmit(String jsonPayload, String signatureBase64,
            AuthorityConfig config, boolean isRetry, int rateLimitRetries) {
        String baseUrl = resolveBaseUrl(config.getEnvironment());
        String token = tokenManager.getTokenWithRetry(config);

        String requestBody;
        try {
            com.fasterxml.jackson.databind.node.ArrayNode sigArray =
                    objectMapper.createArrayNode();
            com.fasterxml.jackson.databind.node.ObjectNode sigEntry =
                    objectMapper.createObjectNode();
            sigEntry.put("signatureType", "I");
            sigEntry.put("value", signatureBase64);
            sigArray.add(sigEntry);

            com.fasterxml.jackson.databind.node.ObjectNode docNode =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                    objectMapper.readTree(jsonPayload);
            docNode.set("signatures", sigArray);

            com.fasterxml.jackson.databind.node.ArrayNode docsArray =
                    objectMapper.createArrayNode();
            docsArray.add(docNode);

            com.fasterxml.jackson.databind.node.ObjectNode wrapper =
                    objectMapper.createObjectNode();
            wrapper.set("documents", docsArray);
            requestBody = objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            log.error("ETA submission request build error", e);
            return SubmissionResultDto.error("Request build error: " + e.getMessage());
        }

        String response;
        try {
            response = restClient.post()
                    .uri(baseUrl + SUBMISSION_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("ETA submission transport error (treat as timeout)", e);
            return SubmissionResultDto.timeout();
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("ETA submission HTTP error: {}", e.getStatusCode(), e);
            if (e.getStatusCode().value() == 401 && !isRetry) {
                tokenManager.invalidateToken(
                        config.getBranch().getId(), config.getEnvironment());
                return doSubmit(jsonPayload, signatureBase64, config, true,
                        rateLimitRetries);
            }
            if (e.getStatusCode().value() == 429) {
                if (rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                    long backoff = resolveRateLimitBackoff(rateLimitRetries);
                    log.info("ETA rate limited, retry {}/{}, backoff {}ms",
                            rateLimitRetries + 1, MAX_RATE_LIMIT_RETRIES, backoff);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return SubmissionResultDto.error("Rate limit retry interrupted");
                    }
                    return doSubmit(jsonPayload, signatureBase64, config,
                            isRetry, rateLimitRetries + 1);
                }
                return SubmissionResultDto.error("ETA rate limit exceeded after "
                        + MAX_RATE_LIMIT_RETRIES + " retries");
            }
            if (e.getStatusCode().is5xxServerError()) {
                return SubmissionResultDto.timeout();
            }
            return SubmissionResultDto.rejected(
                    e.getStatusCode().value(),
                    List.of(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("ETA submission unexpected error", e);
            return SubmissionResultDto.error("Unexpected error: " + e.getMessage());
        }

        return parseResponse(response);
    }

    private SubmissionResultDto parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return SubmissionResultDto.error("Empty response from ETA");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            int overallStatus = root.path("status").asInt(200);

            JsonNode accepted = root.path("acceptedDocuments");
            if (accepted.isArray() && accepted.size() > 0) {
                JsonNode firstAccepted = accepted.get(0);
                String uuid = firstAccepted.path("uuid").asText(null);
                String longId = firstAccepted.path("longId").asText(null);
                String externalRef = uuid != null ? uuid : longId;

                return SubmissionResultDto.successWithExternalRef(
                        overallStatus, response, externalRef);
            }

            JsonNode rejected = root.path("rejectedDocuments");
            if (rejected.isArray() && rejected.size() > 0) {
                JsonNode firstRejected = rejected.get(0);
                List<String> errors = new ArrayList<>();
                String errorMsg = firstRejected.path("error").path("message").asText(null);
                if (errorMsg != null && !errorMsg.isBlank()) {
                    errors.add(errorMsg);
                }
                JsonNode errorDetails = firstRejected.path("error").path("details");
                if (errorDetails != null && errorDetails.isArray()) {
                    for (JsonNode detail : errorDetails) {
                        String msg = detail.path("message").asText("");
                        if (!msg.isBlank()) {
                            errors.add(msg);
                        }
                    }
                }
                if (errors.isEmpty()) {
                    errors.add("Document rejected by ETA");
                }
                return SubmissionResultDto.rejected(overallStatus, errors);
            }

            if (root.has("error")) {
                String message = root.path("error").path("message").asText("");
                List<String> errors = message.isBlank()
                        ? List.of("ETA submission failed") : List.of(message);
                return SubmissionResultDto.rejected(overallStatus, errors);
            }

            return SubmissionResultDto.rejected(overallStatus,
                    List.of("ETA submission failed with status: " + overallStatus));
        } catch (Exception e) {
            log.error("Failed to parse ETA submission response", e);
            return SubmissionResultDto.error("Failed to parse response");
        }
    }

    private String resolveBaseUrl(Environment environment) {
        return switch (environment) {
          case ETA_PREPRODUCTION -> "https://api.preprod.invoicing.eta.gov.eg/api/v1";
          case ETA_PRODUCTION -> "https://api.invoicing.eta.gov.eg/api/v1";
          default -> throw new IllegalArgumentException(
                  "Unsupported ETA environment: " + environment);
        };
    }

    private static long resolveRateLimitBackoff(int retryIndex) {
        return switch (retryIndex) {
          case 0 -> RATE_LIMIT_BACKOFF_FIRST;
          case 1 -> RATE_LIMIT_BACKOFF_SECOND;
          default -> RATE_LIMIT_BACKOFF_THIRD;
        };
    }
}
