package com.einvoice.zatca.client;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.service.CryptoService;
import com.einvoice.core.service.SubmissionResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * ZATCA reporting API client for simplified invoice submission.
 */
@Service
public class ZatcaReportingClient {

    private static final Logger log = LoggerFactory.getLogger(ZatcaReportingClient.class);

    private static final String REPORTING_PATH = "/invoices/reporting/single";
    private static final String ACCEPT_VERSION = "V2";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;

    /**
     * Creates a new ZatcaReportingClient.
     *
     * @param restClientBuilder the REST client builder
     * @param objectMapper the JSON object mapper
     * @param cryptoService the crypto service for credential decryption
     */
    public ZatcaReportingClient(RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper, CryptoService cryptoService) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = restClientBuilder
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
    }

    /**
     * Submits a simplified invoice for reporting to ZATCA.
     *
     * @param base64XmlPayload the Base64-encoded XML invoice
     * @param invoiceHash the invoice hash
     * @param uuid the invoice UUID
     * @param config the authority configuration
     * @return the submission result
     */
    public SubmissionResultDto submitReporting(String base64XmlPayload,
            String invoiceHash, String uuid, AuthorityConfig config) {
        String csid;
        String secret;
        String baseUrl;
        String requestBody;
        try {
            csid = resolveCsid(config);
            secret = resolveSecret(config);
            baseUrl = resolveBaseUrl(config);
            requestBody = buildRequestBody(base64XmlPayload, invoiceHash, uuid);
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("ZATCA reporting configuration error", e);
            return SubmissionResultDto.error("Configuration error: " + e.getMessage());
        } catch (Exception e) {
            log.error("ZATCA reporting request-building error", e);
            return SubmissionResultDto.error("Request build error: " + e.getMessage());
        }

        String authHeader = buildBasicAuth(csid, secret);
        String response;
        try {
            response = restClient.post()
                    .uri(baseUrl + REPORTING_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header("Accept-Version", ACCEPT_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("ZATCA reporting transport error (treat as timeout)", e);
            return SubmissionResultDto.timeout();
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("ZATCA reporting HTTP error: {}", e.getStatusCode(), e);
            if (e.getStatusCode().is5xxServerError()) {
                return SubmissionResultDto.timeout();
            }
            return SubmissionResultDto.rejected(
                    e.getStatusCode().value(),
                    List.of(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("ZATCA reporting unexpected error", e);
            return SubmissionResultDto.error("Unexpected error: " + e.getMessage());
        }

        return parseResponse(response);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String buildRequestBody(String base64XmlPayload,
            String invoiceHash, String uuid) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "invoiceHash", invoiceHash != null ? invoiceHash : "",
                "uuid", uuid != null ? uuid : "",
                "invoice", base64XmlPayload
        ));
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private SubmissionResultDto parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return SubmissionResultDto.error("Empty response from ZATCA reporting");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            List<String> warnings = parseMessages(root.path("warningList"));
            List<String> errors = parseMessages(root.path("errorList"));
            int statusCode = root.path("httpStatusCode").asInt(200);

            if (!errors.isEmpty()) {
                return SubmissionResultDto.rejected(statusCode, errors);
            }

            return SubmissionResultDto.success(statusCode, null, response, warnings);
        } catch (Exception e) {
            log.error("Failed to parse ZATCA reporting response", e);
            return SubmissionResultDto.error("Failed to parse response");
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private List<String> parseMessages(JsonNode messagesNode) {
        if (messagesNode == null || !messagesNode.isArray()) {
            return List.of();
        }
        java.util.List<String> messages = new java.util.ArrayList<>();
        for (JsonNode msg : messagesNode) {
            messages.add(msg.path("message").asText(msg.toString()));
        }
        return messages;
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String resolveCsid(AuthorityConfig config) {
        if (config.getCsidEncrypted() == null) {
            throw new IllegalStateException("CSID not configured for ZATCA");
        }
        byte[] decrypted = cryptoService.decrypt(config.getCsidEncrypted());
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String resolveSecret(AuthorityConfig config) {
        if (config.getCredentialsEncrypted() == null) {
            throw new IllegalStateException("Credentials not configured for ZATCA");
        }
        byte[] decrypted = cryptoService.decrypt(config.getCredentialsEncrypted());
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String buildBasicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String resolveBaseUrl(AuthorityConfig config) {
        return switch (config.getEnvironment()) {
          case ZATCA_SANDBOX -> "https://gw-fatoora.zatca.gov.sa/e-invoicing/developer-portal";
          case ZATCA_SIMULATION -> "https://gw-fatoora.zatca.gov.sa/e-invoicing/simulation";
          case ZATCA_PRODUCTION -> "https://gw-fatoora.zatca.gov.sa/e-invoicing/core";
          default -> throw new IllegalArgumentException(
                  "Unsupported ZATCA environment: " + config.getEnvironment());
        };
    }
}
