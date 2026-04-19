package com.einvoice.eta.client;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.eta.auth.EtaTokenManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * REST client for ETA document operations (PDF download, cancellation).
 */
@Service
public class EtaDocumentClient {

    private static final Logger log = LoggerFactory.getLogger(EtaDocumentClient.class);

    private final RestClient restClient;
    private final EtaTokenManager tokenManager;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new EtaDocumentClient.
     *
     * @param restClientBuilder the REST client builder
     * @param tokenManager the ETA token manager
     * @param objectMapper the JSON object mapper
     */
    public EtaDocumentClient(RestClient.Builder restClientBuilder,
            EtaTokenManager tokenManager, ObjectMapper objectMapper) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Downloads a PDF document from ETA.
     *
     * @param documentId the document identifier
     * @param config the authority configuration
     * @return the PDF bytes, or empty if not available
     */
    public Optional<byte[]> downloadPdf(String documentId, AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config.getEnvironment());
        String token = tokenManager.getToken(config);

        try {
            byte[] pdfBytes = restClient.get()
                    .uri(baseUrl + "/documents/" + documentId + "/pdf")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_PDF)
                    .retrieve()
                    .body(byte[].class);

            return Optional.ofNullable(pdfBytes);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("ETA PDF not available for document {}", documentId);
            } else {
                log.warn("ETA PDF download failed: {}", e.getStatusCode());
                if (e.getStatusCode().value() == 401) {
                    tokenManager.invalidateToken(
                            config.getBranch().getId(), config.getEnvironment());
                }
            }
        } catch (Exception e) {
            log.error("ETA PDF download error", e);
        }

        return Optional.empty();
    }

    /**
     * Cancels a document on the ETA portal.
     *
     * @param documentId the document identifier
     * @param reason the cancellation reason
     * @param config the authority configuration
     * @return true if cancellation succeeded
     */
    public boolean cancelDocument(String documentId, String reason,
            AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config.getEnvironment());
        String token = tokenManager.getToken(config);

        String cancelReason = (reason != null && !reason.isBlank())
                ? reason : "Cancelled by taxpayer";
        String body;
        try {
            body = objectMapper.writeValueAsString(
                    Map.of("status", "Cancelled", "reason", cancelReason));
        } catch (Exception e) {
            log.error("Failed to serialize cancel request body", e);
            return false;
        }

        try {
            restClient.put()
                    .uri(baseUrl + "/documents/" + documentId + "/state")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("ETA document {} cancelled successfully", documentId);
            return true;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("ETA document cancellation failed for {}: {}",
                    documentId, e.getStatusCode());
            return false;
        } catch (Exception e) {
            log.error("ETA document cancellation error for {}", documentId, e);
            return false;
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
}
