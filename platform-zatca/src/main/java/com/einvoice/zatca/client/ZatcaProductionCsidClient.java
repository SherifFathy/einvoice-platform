package com.einvoice.zatca.client;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.service.CryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** HTTP client for exchanging compliance CSID for production CSID and certificate renewal. */
@Service
public class ZatcaProductionCsidClient {

    private static final Logger log = LoggerFactory.getLogger(ZatcaProductionCsidClient.class);

    private static final String PRODUCTION_CSIDS_PATH = "/production/csids";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;

    /**
     * Creates the client with configured timeouts.
     *
     * @param restClientBuilder the REST client builder
     * @param objectMapper the JSON mapper
     * @param cryptoService the crypto service for decrypting credentials
     */
    public ZatcaProductionCsidClient(RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper, CryptoService cryptoService) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
    }

    public record ProductionCsidResult(boolean success, String csid, String secret,
            byte[] certificateBytes, OffsetDateTime certificateExpiry, String error) {}

    /**
     * Exchanges a compliance request ID for a production CSID.
     *
     * @param complianceRequestId the compliance request ID from the compliance step
     * @param config the authority configuration
     * @return the production CSID result
     */
    public ProductionCsidResult exchangeForProductionCsid(String complianceRequestId,
            AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config);
        String csid = resolveCsid(config);
        String secret = resolveSecret(config);
        String authHeader = buildBasicAuth(csid, secret);
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                    "compliance_request_id", complianceRequestId));
        } catch (Exception e) {
            return new ProductionCsidResult(false, null, null, null, null,
                    "Failed to build request: " + e.getMessage());
        }

        String response;
        try {
            response = restClient.post()
                    .uri(baseUrl + PRODUCTION_CSIDS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header("Accept-Version", "V2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("ZATCA production CSID request failed: {}", e.getStatusCode(), e);
            return new ProductionCsidResult(false, null, null, null, null,
                    "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("ZATCA production CSID request timeout", e);
            return new ProductionCsidResult(false, null, null, null, null, "Connection timeout");
        } catch (Exception e) {
            log.error("ZATCA production CSID request unexpected error", e);
            return new ProductionCsidResult(false, null, null, null, null,
                    "Unexpected error: " + e.getMessage());
        }

        return parseProductionCsidResponse(response);
    }

    public record RenewalResult(boolean success, String csid, String secret,
            byte[] certificateBytes, OffsetDateTime certificateExpiry, String error) {}

    /**
     * Renews an existing ZATCA certificate using a new CSR.
     *
     * @param csrBase64 base64-encoded CSR for the new certificate
     * @param config the authority configuration holding the current CSID credentials
     * @return the renewal result with the new certificate details
     */
    public RenewalResult renewCertificate(String csrBase64, AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config);
        String csid = resolveCsid(config);
        String secret = resolveSecret(config);
        String authHeader = buildBasicAuth(csid, secret);
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(java.util.Map.of("csr", csrBase64));
        } catch (Exception e) {
            return new RenewalResult(false, null, null, null, null,
                    "Failed to build request: " + e.getMessage());
        }

        String response;
        try {
            response = restClient.patch()
                    .uri(baseUrl + PRODUCTION_CSIDS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header("Accept-Version", "V2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("ZATCA certificate renewal failed: {}", e.getStatusCode(), e);
            return new RenewalResult(false, null, null, null, null,
                    "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("ZATCA certificate renewal timeout", e);
            return new RenewalResult(false, null, null, null, null, "Connection timeout");
        } catch (Exception e) {
            log.error("ZATCA certificate renewal unexpected error", e);
            return new RenewalResult(false, null, null, null, null,
                    "Unexpected error: " + e.getMessage());
        }

        return parseRenewalResponse(response);
    }

    private ProductionCsidResult parseProductionCsidResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String csid = root.path("binarySecurityToken").asText(null);
            String secret = root.path("secret").asText(null);

            if (csid == null || csid.isBlank()) {
                List<String> errors = parseMessages(root.path("errorList"));
                return new ProductionCsidResult(false, null, null, null, null,
                        errors.isEmpty() ? "Missing binarySecurityToken" : String.join("; ", errors));
            }

            CertificateExtraction extracted = extractCertificate(csid);
            return new ProductionCsidResult(true, csid, secret,
                    extracted.bytes(), extracted.expiry(), null);
        } catch (Exception e) {
            log.error("Failed to parse ZATCA production CSID response", e);
            return new ProductionCsidResult(false, null, null, null, null, "Failed to parse response");
        }
    }

    private RenewalResult parseRenewalResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String csid = root.path("binarySecurityToken").asText(null);
            String secret = root.path("secret").asText(null);

            if (csid == null || csid.isBlank()) {
                List<String> errors = parseMessages(root.path("errorList"));
                return new RenewalResult(false, null, null, null, null,
                        errors.isEmpty() ? "Missing binarySecurityToken" : String.join("; ", errors));
            }

            CertificateExtraction extracted = extractCertificate(csid);
            return new RenewalResult(true, csid, secret,
                    extracted.bytes(), extracted.expiry(), null);
        } catch (Exception e) {
            log.error("Failed to parse ZATCA renewal response", e);
            return new RenewalResult(false, null, null, null, null, "Failed to parse response");
        }
    }

    private record CertificateExtraction(byte[] bytes, OffsetDateTime expiry) {}

    private CertificateExtraction extractCertificate(String binarySecurityToken) {
        try {
            byte[] certBytes = Base64.getDecoder().decode(binarySecurityToken);
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(certBytes));
            OffsetDateTime expiry = cert.getNotAfter().toInstant()
                    .atZone(ZoneOffset.UTC).toOffsetDateTime();
            return new CertificateExtraction(certBytes, expiry);
        } catch (Exception e) {
            log.warn("Could not decode binarySecurityToken as X.509 certificate", e);
            return new CertificateExtraction(null, null);
        }
    }

    private List<String> parseMessages(JsonNode messagesNode) {
        if (messagesNode == null || !messagesNode.isArray()) {
            return List.of();
        }
        List<String> messages = new ArrayList<>();
        for (JsonNode msg : messagesNode) {
            messages.add(msg.path("message").asText(msg.toString()));
        }
        return messages;
    }

    private String resolveCsid(AuthorityConfig config) {
        if (config.getCsidEncrypted() == null) {
            throw new IllegalStateException("CSID not configured for ZATCA");
        }
        byte[] decrypted = cryptoService.decrypt(config.getCsidEncrypted());
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String resolveSecret(AuthorityConfig config) {
        if (config.getCredentialsEncrypted() == null) {
            throw new IllegalStateException("Credentials not configured for ZATCA");
        }
        byte[] decrypted = cryptoService.decrypt(config.getCredentialsEncrypted());
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String buildBasicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }

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
