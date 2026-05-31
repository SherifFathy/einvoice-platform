package com.einvoice.zatca.engine;

import com.einvoice.core.authority.AuthorityCredentials;
import com.einvoice.core.authority.AuthorityEngine;
import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.CancelInput;
import com.einvoice.core.authority.CertificateMaterial;
import com.einvoice.core.authority.DocumentInput;
import com.einvoice.core.authority.SerializedPayload;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.authority.StatusInput;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.zatca.build.ZatcaUblBuilder;
import com.einvoice.zatca.chain.ZatcaChainService;
import com.einvoice.zatca.client.ZatcaHttpClient;
import com.einvoice.zatca.hash.ZatcaHashService;
import com.einvoice.zatca.qr.ZatcaQrService;
import com.einvoice.zatca.sign.ZatcaSigningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Orchestrates ZATCA-specific submission, clearance, reporting and status-check operations. */
@Component
public class ZatcaAuthorityEngine implements AuthorityEngine {

    private static final String CLEARANCE_PATH =
            "/invoices/clearance/single";
    private static final String REPORTING_PATH =
            "/invoices/reporting/single";

    // TODO (#15): ZATCA Phase-2 clearance/reporting endpoints expect a JSON
    // envelope: {"invoiceHash":"...","uuid":"...","invoice":"<base64 UBL>"}
    // Currently we send raw application/xml. This will block real-sandbox calls.
    // Must be resolved alongside #7 (hash input) during integration testing.

    private static final Set<String> POSITIVE_STATUSES = Set.of(
            "CLEARED", "REPORTED",
            "CLEARED_WITH_WARNINGS", "REPORTED_WITH_WARNINGS",
            "IN_REVIEW", "PENDING");

    private static final DateTimeFormatter ISO_8601_COMBINED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ZatcaUblBuilder ublBuilder;
    private final ZatcaSigningService signingService;
    private final ZatcaHashService hashService;
    private final ZatcaQrService qrService;
    private final ZatcaHttpClient httpClient;
    private final ZatcaChainService chainService;
    private final ObjectMapper objectMapper;

    /**
     * Construct the engine with all required services.
     *
     * @param ublBuilder UBL builder
     * @param signingService signing service
     * @param hashService hash service
     * @param qrService QR service
     * @param httpClient ZATCA HTTP client
     * @param chainService chain service
     * @param objectMapper JSON object mapper
     */
    public ZatcaAuthorityEngine(ZatcaUblBuilder ublBuilder,
            ZatcaSigningService signingService,
            ZatcaHashService hashService,
            ZatcaQrService qrService,
            ZatcaHttpClient httpClient,
            ZatcaChainService chainService,
            ObjectMapper objectMapper) {
        this.ublBuilder = ublBuilder;
        this.signingService = signingService;
        this.hashService = hashService;
        this.qrService = qrService;
        this.httpClient = httpClient;
        this.chainService = chainService;
        this.objectMapper = objectMapper;
    }

    public record ZatcaSubmissionResult(
            byte[] ublXml,
            byte[] signedUblXml,
            String invoiceHash,
            String qrBase64,
            byte[] qrPng
    ) {}

    /**
     * Builds, signs, hashes and QR-encodes a standard (B2B) invoice for ZATCA.
     *
     * @param header the standard invoice header
     * @param companyId the owning company identifier
     * @param authorityEnvironmentId the ZATCA environment identifier
     * @return a {@link ZatcaSubmissionResult} containing all prepared artefacts
     */
    public ZatcaSubmissionResult prepareStandardSubmission(
            ZatcaStandardHeader header, UUID companyId,
            Short authorityEnvironmentId) {
        byte[] ublXml = ublBuilder.buildStandardUbl(header);
        byte[] signedXml = signingService.sign(
                ublXml, companyId, authorityEnvironmentId);
        String hash = hashService.computeHash(signedXml);
        List<byte[]> qrTags = buildQrTags(
                header.getSellerData(),
                header.getIssueDate().atTime(header.getIssueTime()),
                header.getTaxInclusiveAmount(),
                header.getTaxAmount(),
                hash);
        String qrBase64 = qrService.encodeTlvBase64(qrTags);
        byte[] qrPng = qrService.renderPng300x300(qrBase64);
        return new ZatcaSubmissionResult(
                ublXml, signedXml, hash, qrBase64, qrPng);
    }

    /**
     * Builds, signs, hashes and QR-encodes a simplified (B2C) invoice for ZATCA.
     *
     * @param header the simplified invoice header
     * @param companyId the owning company identifier
     * @param authorityEnvironmentId the ZATCA environment identifier
     * @return a {@link ZatcaSubmissionResult} containing all prepared artefacts
     */
    public ZatcaSubmissionResult prepareSimplifiedSubmission(
            ZatcaSimplifiedHeader header, UUID companyId,
            Short authorityEnvironmentId) {
        byte[] ublXml = ublBuilder.buildSimplifiedUbl(header);
        byte[] signedXml = signingService.sign(
                ublXml, companyId, authorityEnvironmentId);
        String hash = hashService.computeHash(signedXml);
        List<byte[]> qrTags = buildQrTags(
                header.getSellerData(),
                header.getIssueDate().atTime(header.getIssueTime()),
                header.getTaxInclusiveAmount(),
                header.getTaxAmount(),
                hash);
        String qrBase64 = qrService.encodeTlvBase64(qrTags);
        byte[] qrPng = qrService.renderPng300x300(qrBase64);
        return new ZatcaSubmissionResult(
                ublXml, signedXml, hash, qrBase64, qrPng);
    }

    List<byte[]> buildQrTags(Map<String, Object> sellerData,
            java.time.LocalDateTime issueDateTime,
            java.math.BigDecimal taxInclusiveTotal,
            java.math.BigDecimal vatTotal,
            String invoiceHashHex) {
        List<byte[]> tags = new ArrayList<>(9);
        tags.add(utf8(sellerName(sellerData)));
        tags.add(utf8(vatNumber(sellerData)));
        tags.add(utf8(ISO_8601_COMBINED.format(issueDateTime)));
        tags.add(utf8(taxInclusiveTotal.setScale(2,
                java.math.RoundingMode.HALF_EVEN).toPlainString()));
        tags.add(utf8(vatTotal.setScale(2,
                java.math.RoundingMode.HALF_EVEN).toPlainString()));
        tags.add(HexFormat.of().parseHex(invoiceHashHex));
        tags.add(new byte[0]);
        tags.add(new byte[0]);
        tags.add(new byte[0]);
        return tags;
    }

    private String sellerName(Map<String, Object> sellerData) {
        Object name = sellerData.getOrDefault("partyName",
                sellerData.getOrDefault("name", sellerData.get("nameEn")));
        return name != null ? name.toString() : "";
    }

    private String vatNumber(Map<String, Object> sellerData) {
        Object vat = sellerData.getOrDefault("taxRegistrationNumber",
                sellerData.get("vatNumber"));
        return vat != null ? vat.toString() : "";
    }

    /**
     * Submits a pre-signed standard invoice to the ZATCA clearance endpoint.
     *
     * @param prepared the previously prepared submission result
     * @param companyId the owning company identifier
     * @param authorityEnvironmentId the ZATCA environment identifier
     * @return the parsed ZATCA authority response
     */
    public AuthorityResponse submitClearance(
            ZatcaSubmissionResult prepared,
            UUID companyId, Short authorityEnvironmentId) {
        String baseUrl = httpClient.getBaseUrl(
                companyId, authorityEnvironmentId);
        String responseBody = httpClient.buildClient(baseUrl)
                .post()
                .uri(CLEARANCE_PATH)
                .header("Content-Type", "application/xml")
                .body(prepared.signedUblXml())
                .retrieve()
                .body(String.class);
        return parseZatcaResponse(responseBody);
    }

    /**
     * Submits a pre-signed simplified invoice to the ZATCA reporting endpoint.
     *
     * @param prepared the previously prepared submission result
     * @param companyId the owning company identifier
     * @param authorityEnvironmentId the ZATCA environment identifier
     * @return the parsed ZATCA authority response
     */
    public AuthorityResponse submitReporting(
            ZatcaSubmissionResult prepared,
            UUID companyId, Short authorityEnvironmentId) {
        String baseUrl = httpClient.getBaseUrl(
                companyId, authorityEnvironmentId);
        String responseBody = httpClient.buildClient(baseUrl)
                .post()
                .uri(REPORTING_PATH)
                .header("Content-Type", "application/xml")
                .body(prepared.signedUblXml())
                .retrieve()
                .body(String.class);
        return parseZatcaResponse(responseBody);
    }

    AuthorityResponse parseZatcaResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new AuthorityResponse(
                    false, null, null, null, null,
                    502, "Empty response from ZATCA", null);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String status = root.path("status").asText(null);
            String zatcaUuid = root.path("uuid").asText(null);
            String summary = root.path("error").path("message")
                    .asText(null);
            if (status == null || status.isBlank()) {
                return new AuthorityResponse(
                        false, null, null, null, zatcaUuid,
                        502, "Missing status in ZATCA response",
                        responseBody);
            }
            boolean success = POSITIVE_STATUSES.contains(status);
            Integer statusCode = success ? 200
                    : root.path("error").path("code").asInt(400);
            return new AuthorityResponse(
                    success, status, null, null, zatcaUuid,
                    statusCode, summary, responseBody);
        } catch (Exception e) {
            return new AuthorityResponse(
                    false, null, null, null, null,
                    500, "Failed to parse ZATCA response: "
                            + e.getMessage(),
                    responseBody);
        }
    }

    @Override
    public SerializedPayload serialize(DocumentInput input) {
        byte[] ublXml;
        if (TransactionType.STANDARD.name()
                .equals(input.transactionType())) {
            ublXml = ublBuilder.buildStandardUbl(
                    (ZatcaStandardHeader) input.header());
        } else {
            ublXml = ublBuilder.buildSimplifiedUbl(
                    (ZatcaSimplifiedHeader) input.header());
        }
        return new SerializedPayload(ublXml, "application/xml");
    }

    @Override
    public SignedPayload sign(SerializedPayload payload,
            CertificateMaterial cert) {
        throw new UnsupportedOperationException(
                "Use prepareStandardSubmission/prepareSimplifiedSubmission "
                        + "instead; the SPI sign() lacks company/env context");
    }

    @Override
    public AuthorityResponse submit(SignedPayload signed,
            AuthorityCredentials creds) {
        throw new UnsupportedOperationException(
                "Use submitClearance/submitReporting instead; "
                        + "the SPI submit() lacks authorityEnvironmentId");
    }

    @Override
    public AuthorityResponse cancel(CancelInput input) {
        String baseUrl = httpClient.getBaseUrl(
                input.companyId(), input.authorityEnvironmentId());
        String responseBody = httpClient.buildClient(baseUrl)
                .post()
                .uri("/api/v1/invoices/cancel/" + input.etaUuid())
                .retrieve()
                .body(String.class);
        return parseZatcaResponse(responseBody);
    }

    @Override
    public AuthorityResponse checkStatus(StatusInput input) {
        String baseUrl = httpClient.getBaseUrl(
                input.companyId(), input.authorityEnvironmentId());
        String responseBody = httpClient.buildClient(baseUrl)
                .get()
                .uri("/api/v1/invoices/"
                        + input.etaSubmissionId())
                .retrieve()
                .body(String.class);
        return parseZatcaResponse(responseBody);
    }

    private static byte[] utf8(String value) {
        return value != null
                ? value.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
    }
}
