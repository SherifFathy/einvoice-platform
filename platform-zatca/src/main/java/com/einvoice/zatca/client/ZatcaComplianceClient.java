package com.einvoice.zatca.client;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.service.CryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** HTTP client for ZATCA compliance CSID and test invoice submission endpoints. */
@Service
public class ZatcaComplianceClient {

    private static final Logger log = LoggerFactory.getLogger(ZatcaComplianceClient.class);

    private static final String COMPLIANCE_PATH = "/compliance";
    private static final String COMPLIANCE_INVOICES_PATH = "/compliance/invoices";

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
    public ZatcaComplianceClient(RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper, CryptoService cryptoService) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
    }

    public record ComplianceCsidResult(boolean success, String csid, String secret,
            String requestId, String error) {}

    /**
     * Requests a compliance CSID from ZATCA.
     *
     * @param csrBase64 base64-encoded CSR
     * @param otp one-time password for the request
     * @param config the authority configuration
     * @return the compliance CSID result
     */
    public ComplianceCsidResult requestComplianceCsid(String csrBase64, String otp,
            AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config);
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(java.util.Map.of("csr", csrBase64));
        } catch (Exception e) {
            return new ComplianceCsidResult(false, null, null, null,
                    "Failed to build request: " + e.getMessage());
        }

        String response;
        try {
            var requestSpec = restClient.post()
                    .uri(baseUrl + COMPLIANCE_PATH)
                    .header("Accept-Version", "V2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody);

            if (otp != null && !otp.isBlank()) {
                requestSpec = requestSpec.header("OTP", otp);
            }

            response = requestSpec.retrieve().body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("ZATCA compliance CSID request failed: {}", e.getStatusCode(), e);
            return new ComplianceCsidResult(false, null, null, null,
                    "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("ZATCA compliance CSID request timeout", e);
            return new ComplianceCsidResult(false, null, null, null, "Connection timeout");
        } catch (Exception e) {
            log.error("ZATCA compliance CSID request unexpected error", e);
            return new ComplianceCsidResult(false, null, null, null,
                    "Unexpected error: " + e.getMessage());
        }

        return parseComplianceResponse(response);
    }

    public record TestInvoiceResult(boolean success, String error) {}

    /**
     * Submits a compliance test invoice to ZATCA.
     *
     * @param base64SignedInvoice base64-encoded signed invoice XML
     * @param invoiceHash the invoice hash
     * @param uuid the invoice UUID
     * @param config the authority configuration
     * @return the test invoice result
     */
    public TestInvoiceResult submitTestInvoice(String base64SignedInvoice,
            String invoiceHash, String uuid, AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config);
        String csid = resolveCsid(config);
        String secret = resolveSecret(config);
        String authHeader = buildBasicAuth(csid, secret);
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                    "invoiceHash", invoiceHash != null ? invoiceHash : "",
                    "uuid", uuid != null ? uuid : "",
                    "invoice", base64SignedInvoice));
        } catch (Exception e) {
            return new TestInvoiceResult(false, "Failed to build request: " + e.getMessage());
        }

        String response;
        try {
            response = restClient.post()
                    .uri(baseUrl + COMPLIANCE_INVOICES_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .header("Accept-Version", "V2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("ZATCA test invoice submission failed: {}", e.getStatusCode(), e);
            return new TestInvoiceResult(false,
                    "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("ZATCA test invoice submission timeout", e);
            return new TestInvoiceResult(false, "Connection timeout");
        } catch (Exception e) {
            log.error("ZATCA test invoice submission unexpected error", e);
            return new TestInvoiceResult(false, "Unexpected error: " + e.getMessage());
        }

        return parseTestInvoiceResponse(response);
    }

    private ComplianceCsidResult parseComplianceResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String csid = root.path("binarySecurityToken").asText(null);
            String secret = root.path("secret").asText(null);
            String requestId = root.path("requestId").asText(null);

            if (csid == null || csid.isBlank()) {
                List<String> errors = parseMessages(root.path("errorList"));
                return new ComplianceCsidResult(false, null, null, null,
                        errors.isEmpty() ? "Missing binarySecurityToken in response" : String.join("; ", errors));
            }
            return new ComplianceCsidResult(true, csid, secret, requestId, null);
        } catch (Exception e) {
            log.error("Failed to parse ZATCA compliance response", e);
            return new ComplianceCsidResult(false, null, null, null, "Failed to parse response");
        }
    }

    private TestInvoiceResult parseTestInvoiceResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<String> errors = parseMessages(root.path("errorList"));
            if (!errors.isEmpty()) {
                return new TestInvoiceResult(false, String.join("; ", errors));
            }
            return new TestInvoiceResult(true, null);
        } catch (Exception e) {
            log.error("Failed to parse ZATCA test invoice response", e);
            return new TestInvoiceResult(false, "Failed to parse response");
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
