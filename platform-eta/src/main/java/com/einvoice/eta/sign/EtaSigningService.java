package com.einvoice.eta.sign;

import com.einvoice.core.authority.CertificateMaterial;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.error.NoCertificateConfiguredException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

/** Signs ETA invoice payloads using CAdES-BES with BouncyCastle. */
@Component
public class EtaSigningService {

    /**
     * Signs the given canonical bytes using the provided certificate material.
     *
     * @param canonicalBytes the raw document bytes to sign
     * @param certMaterial the certificate and private key material
     * @return the signed payload containing the original bytes and signature
     */
    public SignedPayload sign(byte[] canonicalBytes,
            CertificateMaterial certMaterial) {
        if (certMaterial == null
                || certMaterial.certificate() == null
                || certMaterial.certificate().isBlank()
                || certMaterial.privateKey() == null
                || certMaterial.privateKey().isBlank()) {
            throw new NoCertificateConfiguredException(
                    "No certificate configured",
                    null, null);
        }

        try {
            X509Certificate certificate = parseCertificate(
                    certMaterial.certificate());
            PrivateKey privateKey = parsePrivateKey(
                    certMaterial.privateKey());

            CMSTypedData data = new CMSProcessableByteArray(
                    canonicalBytes);
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            ContentSigner signer = new JcaContentSignerBuilder(
                    "SHA256withRSA").build(privateKey);
            gen.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder()
                                    .build())
                            .build(signer, certificate));

            var certHolder = new org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(
                    certificate);
            gen.addCertificate(certHolder);

            byte[] signature = gen.generate(data, true).getEncoded();
            return new SignedPayload(
                    canonicalBytes, signature, "CAdES-BES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign payload", e);
        }
    }

    private X509Certificate parseCertificate(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(decoded));
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        java.security.KeyFactory keyFactory =
                java.security.KeyFactory.getInstance("RSA");
        java.security.spec.PKCS8EncodedKeySpec keySpec =
                new java.security.spec.PKCS8EncodedKeySpec(decoded);
        return keyFactory.generatePrivate(keySpec);
    }
}
