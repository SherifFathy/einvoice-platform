package com.einvoice.eta.signing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.service.CryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EtaSigningServiceTest {

    private EtaSigningService signingService;
    private KeyPair rsaKeyPair;
    private X509Certificate certificate;
    private AuthorityConfig config;

    @BeforeAll
    static void registerProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        rsaKeyPair = keyGen.generateKeyPair();

        X500Name issuerName = new X500Name("CN=Test,O=Test,C=EG");
        X500Name subjectName = new X500Name("CN=Test,O=Test,C=EG");
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerName, serial, notBefore, notAfter, subjectName, rsaKeyPair.getPublic());

        certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certBuilder.build(
                        new JcaContentSignerBuilder("SHA256withRSA")
                                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                .build(rsaKeyPair.getPrivate())));

        CryptoService cryptoService = new CryptoService() {
            @Override
            public byte[] encrypt(byte[] plaintext) {
                return plaintext;
            }

            @Override
            public byte[] decrypt(byte[] encrypted) {
                return encrypted;
            }
        };

        signingService = new EtaSigningService(cryptoService);

        byte[] certBytes = certificate.getEncoded();
        byte[] privateKeyBytes = rsaKeyPair.getPrivate().getEncoded();

        config = AuthorityConfig.builder()
                .branch(Branch.builder().id(1L).build())
                .environment(Environment.ETA_PREPRODUCTION)
                .certificateEncrypted(certBytes)
                .privateKeyEncrypted(privateKeyBytes)
                .build();
    }

    @Test
    void sign_producesNonEmptyBase64Signature() {
        String payload = "{\"documentTypeCode\":\"i\",\"internalID\":\"INV-001\"}";
        EtaSigningService.EtaSigningResult result = signingService.sign(payload, config);

        assertNotNull(result.signatureBase64());
        assertFalse(result.signatureBase64().isEmpty());

        byte[] decoded = Base64.getDecoder().decode(result.signatureBase64());
        assertTrue(decoded.length > 0, "Signature should have non-zero length");
    }

    @Test
    void sign_producesValidCmsSignedData() {
        String payload = "{\"documentTypeCode\":\"i\"}";
        EtaSigningService.EtaSigningResult result = signingService.sign(payload, config);

        assertNotNull(result.signedData());
        assertTrue(result.signedData().isDetachedSignature(),
                "Should produce detached CMS signature");

        assertFalse(result.signedData().getSignerInfos().getSigners().isEmpty(),
                "Should contain signer info");
    }

    @Test
    void sign_containsCertificateInSignedData() {
        String payload = "{\"documentTypeCode\":\"i\"}";
        EtaSigningService.EtaSigningResult result = signingService.sign(payload, config);

        var certStore = result.signedData().getCertificates();
        var matches = certStore.getMatches(null);
        assertFalse(matches.isEmpty(), "Signed data should contain certificates");
    }

    @Test
    void sign_differentPayloadsProduceDifferentSignatures() {
        String payload1 = "{\"documentTypeCode\":\"i\",\"internalID\":\"INV-001\"}";
        String payload2 = "{\"documentTypeCode\":\"i\",\"internalID\":\"INV-002\"}";

        EtaSigningService.EtaSigningResult result1 = signingService.sign(payload1, config);
        EtaSigningService.EtaSigningResult result2 = signingService.sign(payload2, config);

        assertFalse(result1.signatureBase64().equals(result2.signatureBase64()),
                "Different payloads should produce different signatures");
    }

    @Test
    void sign_throwsOnMissingCertificate() {
        AuthorityConfig badConfig = AuthorityConfig.builder()
                .branch(Branch.builder().id(1L).build())
                .environment(Environment.ETA_PREPRODUCTION)
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                EtaSigningService.EtaSigningException.class,
                () -> signingService.sign("{}", badConfig));
    }
}
