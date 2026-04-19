package com.einvoice.zatca;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.ArtifactType;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.service.AuthorityEngine;
import com.einvoice.core.service.CryptoService;
import com.einvoice.core.service.SubmissionResultDto;
import com.einvoice.zatca.client.ZatcaClearanceClient;
import com.einvoice.zatca.client.ZatcaReportingClient;
import com.einvoice.zatca.hash.ZatcaHashService;
import com.einvoice.zatca.qr.ZatcaQrService;
import com.einvoice.zatca.signing.ZatcaSigningService;
import com.einvoice.zatca.xml.ZatcaUblBuilder;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stateless ZATCA authority engine.
 *
 * <p>No mutable instance fields — safe for concurrent use as a Spring singleton.</p>
 */
@Service
public class ZatcaAuthorityEngine implements AuthorityEngine {

    private static final Logger log = LoggerFactory.getLogger(ZatcaAuthorityEngine.class);

    private final ZatcaUblBuilder ublBuilder;
    private final ZatcaHashService hashService;
    private final ZatcaSigningService signingService;
    private final ZatcaQrService qrService;
    private final ZatcaClearanceClient clearanceClient;
    private final ZatcaReportingClient reportingClient;
    private final CryptoService cryptoService;

    /**
     * Creates a new ZatcaAuthorityEngine.
     *
     * @param ublBuilder the UBL XML builder
     * @param hashService the hash computation service
     * @param signingService the XML signing service
     * @param qrService the QR encoding service
     * @param clearanceClient the ZATCA clearance API client
     * @param reportingClient the ZATCA reporting API client
     * @param cryptoService the crypto service
     */
    public ZatcaAuthorityEngine(ZatcaUblBuilder ublBuilder,
            ZatcaHashService hashService,
            ZatcaSigningService signingService,
            ZatcaQrService qrService,
            ZatcaClearanceClient clearanceClient,
            ZatcaReportingClient reportingClient,
            CryptoService cryptoService) {
        this.ublBuilder = ublBuilder;
        this.hashService = hashService;
        this.signingService = signingService;
        this.qrService = qrService;
        this.clearanceClient = clearanceClient;
        this.reportingClient = reportingClient;
        this.cryptoService = cryptoService;
    }

    @Override
    public Authority getSupportedAuthority() {
        return Authority.ZATCA;
    }

    @Override
    public String generatePayload(Invoice invoice, AuthorityConfig config) {
        long counter = config != null && config.getInvoiceCounter() != null
                ? config.getInvoiceCounter() + 1L : 1L;
        String storedPreviousHash = config != null ? config.getPreviousInvoiceHash() : null;
        String previousHash = hashService.getPreviousHashBase64(storedPreviousHash);
        String xml = ublBuilder.buildXml(invoice, counter, previousHash);
        log.debug("Generated UBL XML for invoice {} (ICV={}, PIH={})",
                invoice.getId(), counter,
                previousHash.length() > 16 ? previousHash.substring(0, 16) + "..." : previousHash);
        return xml;
    }

    @Override
    public SubmissionResultDto submit(Invoice invoice, String payload,
                                      AuthorityConfig config) {
        try {
            String invoiceHash = hashService.computeHash(payload);

            X509Certificate certificate = loadCertificate(config);
            PrivateKey privateKey = loadPrivateKey(config);
            ZatcaSigningService.SigningResult signingResult =
                    signingService.sign(payload, certificate, privateKey);

            String signedXml = signingResult.signedXml();
            byte[] signatureBytes = Base64.getDecoder()
                    .decode(signingResult.signatureValueBase64());
            byte[] publicKey = certificate.getPublicKey().getEncoded();

            String base64Xml = Base64.getEncoder().encodeToString(
                    signedXml.getBytes(StandardCharsets.UTF_8));

            SubmissionResultDto authorityResult;
            if (isSimplifiedInvoice(invoice)) {
                authorityResult = reportingClient.submitReporting(
                        base64Xml, invoiceHash, invoice.getId().toString(), config);
            } else {
                authorityResult = clearanceClient.submitClearance(
                        base64Xml, invoiceHash, invoice.getId().toString(), config);
            }

            String qrBase64 = generateQrBase64(
                    invoice, invoiceHash, signatureBytes, publicKey);
            String signedXmlWithQr = embedQrInSignedXml(signedXml, qrBase64);
            Map<ArtifactType, String> artifacts = new HashMap<>();
            artifacts.put(ArtifactType.QR_CODE, qrBase64);
            artifacts.put(ArtifactType.SIGNED_XML, signedXmlWithQr);

            return new SubmissionResultDto(
                    authorityResult.status(),
                    authorityResult.httpStatusCode(),
                    authorityResult.clearedDocument(),
                    authorityResult.authorityResponse(),
                    authorityResult.warnings(),
                    authorityResult.errors(),
                    artifacts,
                    authorityResult.externalReference());
        } catch (Exception e) {
            log.error("ZATCA submission failed", e);
            return SubmissionResultDto.error(
                    "ZATCA submission failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private boolean isSimplifiedInvoice(Invoice invoice) {
        if (invoice.getType() == InvoiceType.SIMPLIFIED_TAX_INVOICE) {
            return true;
        }
        if (invoice.getType() == InvoiceType.CREDIT_NOTE
                || invoice.getType() == InvoiceType.DEBIT_NOTE) {
            return hasSimplifiedFlag(invoice.getSubtypeFlags());
        }
        return false;
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private boolean hasSimplifiedFlag(String subtypeFlags) {
        if (subtypeFlags == null || subtypeFlags.isBlank()) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(subtypeFlags);
            return node.path("simplified").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String computeInvoiceHash(String payload) {
        return hashService.computeHash(payload);
    }

    @Override
    public ArtifactType getPayloadArtifactType() {
        return ArtifactType.SIGNED_XML;
    }

    @Override
    public ArtifactType getResponseArtifactType() {
        return ArtifactType.ZATCA_RESPONSE;
    }

    @Override
    public ArtifactType getClearedArtifactType() {
        return ArtifactType.CLEARED_XML;
    }

    @Override
    public List<ArtifactType> getExpectedArtifactTypes() {
        return List.of(
                ArtifactType.SIGNED_XML,
                ArtifactType.ZATCA_RESPONSE,
                ArtifactType.CLEARED_XML,
                ArtifactType.QR_CODE
        );
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String generateQrBase64(Invoice invoice, String invoiceHash,
            byte[] signature, byte[] publicKey) {
        String sellerName = invoice.getCompany() != null
                ? invoice.getCompany().getNameEn() : "";
        String vatNumber = invoice.getCompany() != null
                ? invoice.getCompany().getVatNumber() : "";
        java.time.OffsetDateTime issuedAt = invoice.getCreatedAt() != null
                ? invoice.getCreatedAt()
                : invoice.getIssueDate().atStartOfDay()
                        .atZone(java.time.ZoneOffset.UTC).toOffsetDateTime();
        String timestamp = issuedAt
                .atZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return qrService.encodeTlvBase64(
                sellerName, vatNumber, timestamp,
                invoice.getTotalWithVat(), invoice.getTotalVat(),
                invoiceHash, signature, publicKey);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private X509Certificate loadCertificate(AuthorityConfig config) throws Exception {
        if (config.getCertificateEncrypted() == null) {
            throw new IllegalStateException("Certificate not configured for ZATCA");
        }
        byte[] certBytes = cryptoService.decrypt(config.getCertificateEncrypted());
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(certBytes));
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private PrivateKey loadPrivateKey(AuthorityConfig config) throws Exception {
        if (config.getPrivateKeyEncrypted() == null) {
            throw new IllegalStateException("Private key not configured for ZATCA");
        }
        byte[] keyBytes = cryptoService.decrypt(config.getPrivateKeyEncrypted());
        java.security.KeyFactory keyFactory =
                java.security.KeyFactory.getInstance("RSA");
        java.security.spec.PKCS8EncodedKeySpec keySpec =
                new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Rewrites the signed XML so that AdditionalDocumentReference[ID='QR']
     * carries the computed TLV base64 in EmbeddedDocumentBinaryObject.
     */
    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String embedQrInSignedXml(String signedXml, String qrBase64) {
        try {
            javax.xml.parsers.DocumentBuilderFactory f =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
            org.w3c.dom.Document doc = b.parse(new java.io.ByteArrayInputStream(
                    signedXml.getBytes(StandardCharsets.UTF_8)));
            org.w3c.dom.NodeList refs = doc.getElementsByTagNameNS(
                    "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
                    "AdditionalDocumentReference");
            for (int i = 0; i < refs.getLength(); i++) {
                org.w3c.dom.Element ref = (org.w3c.dom.Element) refs.item(i);
                org.w3c.dom.NodeList ids = ref.getElementsByTagNameNS(
                        "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
                        "ID");
                if (ids.getLength() > 0 && "QR".equals(ids.item(0).getTextContent())) {
                    org.w3c.dom.NodeList bins = ref.getElementsByTagNameNS(
                            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
                            "EmbeddedDocumentBinaryObject");
                    if (bins.getLength() > 0) {
                        bins.item(0).setTextContent(qrBase64);
                    }
                    break;
                }
            }
            javax.xml.transform.Transformer t =
                    javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
            t.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
            java.io.StringWriter w = new java.io.StringWriter();
            t.transform(new javax.xml.transform.dom.DOMSource(doc),
                    new javax.xml.transform.stream.StreamResult(w));
            return w.toString();
        } catch (Exception e) {
            log.warn("Failed to embed QR into signed XML; returning original", e);
            return signedXml;
        }
    }
}
