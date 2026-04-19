package com.einvoice.zatca.onboarding;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Generates PKCS#10 certificate signing requests for ZATCA onboarding. */
@Service
public class ZatcaCsrGenerator {

    private static final Logger log = LoggerFactory.getLogger(ZatcaCsrGenerator.class);
    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSA";

    public record CsrResult(String csrBase64, byte[] privateKeyDer) {}

    /**
     * Generates a new RSA key pair and PKCS#10 CSR for ZATCA onboarding.
     *
     * @param commonName the certificate common name
     * @param organizationUnit the organization unit
     * @param organization the organization name
     * @param country the two-letter country code
     * @param serialNumber the device serial number
     * @return the CSR in base64 and the private key DER bytes
     */
    public CsrResult generateCsr(String commonName, String organizationUnit,
            String organization, String country, String serialNumber) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(KEY_SIZE);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            String dn = buildDistinguishedName(commonName, organizationUnit, organization, country, serialNumber);
            X500Name subject = new X500Name(dn);

            JcaPKCS10CertificationRequestBuilder csrBuilder =
                    new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .build(keyPair.getPrivate());

            PKCS10CertificationRequest csr = csrBuilder.build(signer);
            String csrBase64 = Base64.getEncoder().encodeToString(csr.getEncoded());
            byte[] privateKeyDer = keyPair.getPrivate().getEncoded();

            log.info("Generated CSR for CN={}, serialNumber={}", commonName, serialNumber);
            return new CsrResult(csrBase64, privateKeyDer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CSR: " + e.getMessage(), e);
        }
    }

    private String buildDistinguishedName(String commonName, String organizationUnit,
            String organization, String country, String serialNumber) {
        StringBuilder dn = new StringBuilder();
        if (serialNumber != null && !serialNumber.isBlank()) {
            dn.append("SERIALNUMBER=").append(serialNumber);
        }
        if (commonName != null && !commonName.isBlank()) {
            if (!dn.isEmpty()) {
                dn.append(",");
            }
            dn.append("CN=").append(commonName);
        }
        if (organizationUnit != null && !organizationUnit.isBlank()) {
            dn.append(",OU=").append(organizationUnit);
        }
        if (organization != null && !organization.isBlank()) {
            dn.append(",O=").append(organization);
        }
        if (country != null && !country.isBlank()) {
            dn.append(",C=").append(country);
        }
        return dn.toString();
    }
}
