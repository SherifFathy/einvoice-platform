package com.einvoice.zatca.sign;

import com.einvoice.core.domain.config.ZatcaConfig;
import com.einvoice.core.repository.config.ZatcaConfigRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import xades4j.algorithms.EnvelopedSignatureTransform;
import xades4j.production.DataObjectReference;
import xades4j.production.SignedDataObjects;
import xades4j.production.XadesBesSigningProfile;
import xades4j.production.XadesSigner;
import xades4j.providers.impl.DirectKeyingDataProvider;

/** Signs UBL XML documents with XAdES-BES for ZATCA submission. */
@Component
public class ZatcaSigningService {

    private static final Short ZATCA_PRODUCTION_ENV_ID = 3;

    private final ZatcaConfigRepository configRepository;

    public ZatcaSigningService(ZatcaConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Signs the given UBL XML using the XAdES-BES profile.
     *
     * @param ublXml the raw UBL XML bytes
     * @param companyId the owning company identifier
     * @param authorityEnvironmentId the ZATCA environment identifier
     * @return the signed UBL XML bytes
     */
    public byte[] sign(byte[] ublXml, UUID companyId,
            Short authorityEnvironmentId) {
        ZatcaConfig config = configRepository
                .findByCompanyAndAuthorityEnvironment(
                        companyId, authorityEnvironmentId)
                .orElseThrow(() -> new com.einvoice.core.error
                        .NoCertificateConfiguredException(
                        "No ZATCA config for company",
                        companyId, authorityEnvironmentId));

        X509Certificate certificate = decodeCertificate(
                resolveCertificate(config, authorityEnvironmentId));
        PrivateKey privateKey = decodePrivateKey(config.getPrivateKey());

        return doXadesBesSign(ublXml, certificate, privateKey);
    }

    private String resolveCertificate(ZatcaConfig config,
            Short authorityEnvironmentId) {
        if (ZATCA_PRODUCTION_ENV_ID.equals(authorityEnvironmentId)) {
            if (config.getProductionCertificate() != null
                    && !config.getProductionCertificate().isBlank()) {
                return config.getProductionCertificate();
            }
        }
        if (config.getComplianceCertificate() != null
                && !config.getComplianceCertificate().isBlank()) {
            return config.getComplianceCertificate();
        }
        if (config.getProductionCertificate() != null
                && !config.getProductionCertificate().isBlank()) {
            return config.getProductionCertificate();
        }
        throw new com.einvoice.core.error.NoCertificateConfiguredException(
                "No ZATCA certificate configured",
                config.getCompanyId(), authorityEnvironmentId);
    }

    private byte[] doXadesBesSign(byte[] ublXml,
            X509Certificate certificate, PrivateKey privateKey) {
        try {
            DocumentBuilderFactory dbf =
                    DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(ublXml));

            DirectKeyingDataProvider keyingProvider =
                    new DirectKeyingDataProvider(certificate, privateKey);

            XadesSigner signer = new XadesBesSigningProfile(
                    keyingProvider).newSigner();

            DataObjectReference ref = new DataObjectReference("");
            ref.withTransform(new EnvelopedSignatureTransform());
            SignedDataObjects dataObjs = new SignedDataObjects(ref);

            signer.sign(dataObjs, doc.getDocumentElement());

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(doc), new StreamResult(bos));
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("XAdES-BES signing failed", e);
        }
    }

    private X509Certificate decodeCertificate(String base64Cert) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Cert);
            CertificateFactory cf =
                    CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(decoded));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to decode certificate", e);
        }
    }

    private PrivateKey decodePrivateKey(String base64Key) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            try {
                return KeyFactory.getInstance("EC")
                        .generatePrivate(spec);
            } catch (Exception ecFailed) {
                return KeyFactory.getInstance("RSA")
                        .generatePrivate(spec);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to decode private key", e);
        }
    }
}
