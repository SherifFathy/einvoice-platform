package com.einvoice.eta.client;

import com.einvoice.core.authority.AuthorityResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** HTTP client for communicating with the ETA authority APIs. */
@Component
public class EtaHttpClient {

    private static final Short ENV_PRODUCTION = (short) 1;
    private static final Short ENV_PREPROD = (short) 2;

    private static final Map<Short, String> BASE_URLS = Map.of(
            ENV_PRODUCTION, "https://api.invoicing.eta.gov.eg",
            ENV_PREPROD, "https://api.preproduction.invoicing.eta.gov.eg"
    );

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs an EtaHttpClient with the given ObjectMapper.
     *
     * @param objectMapper the JSON object mapper
     */
    public EtaHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Resolves the authority environment ID from a submission URL.
     *
     * @param url the submission URL
     * @return the environment ID, or null if unrecognized
     */
    public Short resolveEnvironmentId(String url) {
        if (url == null) {
            return null;
        }
        for (Map.Entry<Short, String> entry : BASE_URLS.entrySet()) {
            if (url.startsWith(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Requests an OAuth access token from the ETA authority.
     *
     * @param clientId the OAuth client identifier
     * @param clientSecret the OAuth client secret
     * @param tokenUrl the token endpoint URL
     * @return the access token string
     */
    public String requestToken(String clientId, String clientSecret,
            String tokenUrl) {
        String raw = requestTokenRaw(clientId, clientSecret, tokenUrl);
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse token response", e);
        }
    }

    /**
     * Requests an OAuth token and returns the raw JSON response.
     *
     * @param clientId the OAuth client identifier
     * @param clientSecret the OAuth client secret
     * @param tokenUrl the token endpoint URL
     * @return the raw JSON response body
     */
    public String requestTokenRaw(String clientId, String clientSecret,
            String tokenUrl) {
        return restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=" + clientId
                        + "&client_secret=" + clientSecret)
                .retrieve()
                .body(String.class);
    }

    /**
     * Submits an invoice to the ETA authority.
     *
     * @param token the OAuth bearer token
     * @param authorityEnvironmentId the ETA environment identifier
     * @param signedPayload the signed invoice payload
     * @param documentId the internal document identifier
     * @return the authority response
     */
    public AuthorityResponse submitInvoice(String token,
            Short authorityEnvironmentId, byte[] signedPayload,
            UUID documentId) {
        return doPost("/api/v1/documentsubmissions", token,
                authorityEnvironmentId, signedPayload);
    }

    /**
     * Submits a receipt to the ETA authority.
     *
     * @param token the OAuth bearer token
     * @param authorityEnvironmentId the ETA environment identifier
     * @param signedPayload the signed receipt payload
     * @param documentId the internal document identifier
     * @return the authority response
     */
    public AuthorityResponse submitReceipt(String token,
            Short authorityEnvironmentId, byte[] signedPayload,
            UUID documentId) {
        return doPost("/api/v1/receiptsubmissions", token,
                authorityEnvironmentId, signedPayload);
    }

    /**
     * Cancels a previously submitted document.
     *
     * @param token the OAuth bearer token
     * @param authorityEnvironmentId the ETA environment identifier
     * @param etaUuid the ETA document UUID
     * @param reason the cancellation reason
     * @return the authority response
     */
    public AuthorityResponse cancelDocument(String token,
            Short authorityEnvironmentId, String etaUuid, String reason) {
        String baseUrl = resolveBaseUrlOrThrow(authorityEnvironmentId);
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("reason", reason));
            String response = restClient.put()
                    .uri(baseUrl + "/api/v1/documents/"
                            + etaUuid + "/state")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, null);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * Retrieves the status of a submitted document.
     *
     * @param token the OAuth bearer token
     * @param authorityEnvironmentId the ETA environment identifier
     * @param etaSubmissionId the ETA submission identifier
     * @return the authority response
     */
    public AuthorityResponse getDocumentStatus(String token,
            Short authorityEnvironmentId, String etaSubmissionId) {
        String baseUrl = resolveBaseUrlOrThrow(authorityEnvironmentId);
        try {
            String response = restClient.get()
                    .uri(baseUrl + "/api/v1/documents/"
                            + etaSubmissionId + "/details")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, null);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    private AuthorityResponse doPost(String path, String token,
            Short authorityEnvironmentId, byte[] payload) {
        String baseUrl = resolveBaseUrlOrThrow(authorityEnvironmentId);
        try {
            String response = restClient.post()
                    .uri(baseUrl + path)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, null);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    private String resolveBaseUrlOrThrow(Short authorityEnvironmentId) {
        String baseUrl = BASE_URLS.get(authorityEnvironmentId);
        if (baseUrl == null) {
            throw new IllegalArgumentException(
                    "Unknown authority_environment_id: "
                            + authorityEnvironmentId);
        }
        return baseUrl;
    }

    private AuthorityResponse handleException(Exception e) {
        boolean isTimeout = e instanceof ResourceAccessException
                && e.getCause() instanceof SocketTimeoutException;
        if (isTimeout) {
            return new AuthorityResponse(false, "TIMEOUT", null, null,
                    null, null, "Request timed out", null);
        }
        return new AuthorityResponse(false, "ERROR", null, null, null,
                null, e.getMessage(), null);
    }

    private AuthorityResponse parseResponse(String responseBody,
            Integer statusCode) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            boolean success = !node.has("error");
            return new AuthorityResponse(
                    success,
                    success ? "SUCCESS" : "REJECTED",
                    node.path("uuid").asText(null),
                    node.path("longId").asText(null),
                    node.path("submissionId").asText(null),
                    statusCode,
                    node.path("error").path("details").asText(null),
                    responseBody);
        } catch (Exception e) {
            return new AuthorityResponse(false, "ERROR", null, null, null,
                    statusCode, e.getMessage(), responseBody);
        }
    }
}
