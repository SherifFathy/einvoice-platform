package com.einvoice.eta.signing;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.service.CryptoService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuerSerial;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Signs ETA invoice payloads using CAdES-BES detached signatures
 * with ESSCertIDv2 (signing-certificate-v2) attribute.
 */
@Service
public class EtaSigningService {

    private static final Logger log = LoggerFactory.getLogger(EtaSigningService.class);

    private final CryptoService cryptoService;

    /**
     * Creates a new EtaSigningService.
     *
     * @param cryptoService the crypto service for key decryption
     */
    public EtaSigningService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * Signs a JSON payload using CAdES-BES detached signature.
     *
     * @param jsonPayload the JSON invoice payload
     * @param config the authority configuration
     * @return the signing result containing the Base64-encoded signature
     */
    public EtaSigningResult sign(String jsonPayload, AuthorityConfig config) {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            X509Certificate certificate = loadCertificate(config);
            PrivateKey privateKey = loadPrivateKey(config);

            final CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

            final ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(privateKey);

            DigestCalculatorProvider digestProvider =
                    new JcaDigestCalculatorProviderBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build();

            X509CertificateHolder certHolder =
                    new X509CertificateHolder(certificate.getEncoded());

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] certHash = sha256.digest(certHolder.getEncoded());

            GeneralName issuerName = new GeneralName(
                    org.bouncycastle.asn1.x500.X500Name.getInstance(
                            certificate.getIssuerX500Principal().getEncoded()));
            IssuerSerial issuerSerial = new IssuerSerial(
                    new GeneralNames(issuerName),
                    certificate.getSerialNumber());

            ESSCertIDv2 essCertId = new ESSCertIDv2(
                    new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
                    certHash, issuerSerial);

            SigningCertificateV2 signingCertV2 =
                    new SigningCertificateV2(new ESSCertIDv2[]{essCertId});

            final SigningCertificateV2 finalSigningCertV2 = signingCertV2;
            CMSAttributeTableGenerator signedAttrGen =
                    new DefaultSignedAttributeTableGenerator() {
                        @Override
                        @SuppressWarnings("rawtypes")
                        public AttributeTable getAttributes(java.util.Map params) {
                            AttributeTable attrs = super.getAttributes(params);
                            return attrs.add(
                                    PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                                    finalSigningCertV2);
                        }
                    };

            JcaSignerInfoGeneratorBuilder sigInfoGenBuilder =
                    new JcaSignerInfoGeneratorBuilder(digestProvider);
            sigInfoGenBuilder.setSignedAttributeGenerator(signedAttrGen);

            gen.addSignerInfoGenerator(
                    sigInfoGenBuilder.build(signer, certHolder));

            Store certStore = new JcaCertStore(java.util.List.of(certificate));
            gen.addCertificates(certStore);

            byte[] dataToSign = jsonPayload.getBytes(StandardCharsets.UTF_8);
            CMSSignedData signedData = gen.generate(
                    new CMSProcessableByteArray(dataToSign), false);

            byte[] signatureBytes = signedData.getEncoded();
            String signatureBase64 = Base64.getEncoder()
                    .encodeToString(signatureBytes);

            log.debug("ETA CAdES-BES detached signature created ({} bytes base64)",
                    signatureBase64.length());

            return new EtaSigningResult(signatureBase64, signedData);
        } catch (Exception e) {
            log.error("ETA signing failed", e);
            throw new EtaSigningException(
                    "CAdES-BES signing failed: " + e.getMessage(), e);
        }
    }

    private X509Certificate loadCertificate(AuthorityConfig config) throws Exception {
        if (config.getCertificateEncrypted() == null) {
            throw new IllegalStateException("Certificate not configured for ETA");
        }
        byte[] certBytes = cryptoService.decrypt(config.getCertificateEncrypted());
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(certBytes));
    }

    private PrivateKey loadPrivateKey(AuthorityConfig config) throws Exception {
        if (config.getPrivateKeyEncrypted() == null) {
            throw new IllegalStateException("Private key not configured for ETA");
        }
        byte[] keyBytes = cryptoService.decrypt(config.getPrivateKeyEncrypted());
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        java.security.spec.PKCS8EncodedKeySpec keySpec =
                new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
        return keyFactory.generatePrivate(keySpec);
    }

    public record EtaSigningResult(String signatureBase64, CMSSignedData signedData) {
    }

    /**
     * Exception thrown when ETA signing fails.
     */
    public static class EtaSigningException extends RuntimeException {
        public EtaSigningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
