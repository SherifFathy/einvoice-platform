package com.einvoice.eta.sign;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.authority.CertificateMaterial;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.error.NoCertificateConfiguredException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EtaSigningServiceTest {

    private static String testCertPem;
    private static String testKeyPem;
    private EtaSigningService service;

    @BeforeAll
    static void generateTestCertificate() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        X500Name issuer = new X500Name("CN=Test,O=Test,C=EG");
        X509v3CertificateBuilder certBuilder =
                new JcaX509v3CertificateBuilder(
                        issuer, BigInteger.ONE,
                        new Date(System.currentTimeMillis() - 86400000L),
                        new Date(System.currentTimeMillis()
                                + 365L * 86400000L),
                        issuer, keyPair.getPublic());
        X509Certificate cert = new JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(
                        new JcaContentSignerBuilder("SHA256withRSA")
                                .build(keyPair.getPrivate())));

        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN CERTIFICATE-----\n");
        sb.append(Base64.getEncoder()
                .encodeToString(cert.getEncoded()));
        sb.append("\n-----END CERTIFICATE-----\n");
        testCertPem = sb.toString();

        StringBuilder kb = new StringBuilder();
        kb.append("-----BEGIN PRIVATE KEY-----\n");
        kb.append(Base64.getEncoder().encodeToString(
                keyPair.getPrivate().getEncoded()));
        kb.append("\n-----END PRIVATE KEY-----\n");
        testKeyPem = kb.toString();
    }

    @BeforeEach
    void setUp() {
        service = new EtaSigningService();
    }

    @Test
    void signReturnsNonEmptySignature() {
        CertificateMaterial cert = new CertificateMaterial(
                testCertPem, testKeyPem);
        byte[] data = "test-data".getBytes();
        SignedPayload result = service.sign(data, cert);
        assertNotNull(result);
        assertNotNull(result.signature());
        assertTrue(result.signature().length > 0);
        assertTrue(result.canonicalBytes().length > 0);
    }

    @Test
    void signingSameInputTwiceIsDeterministic() {
        CertificateMaterial cert = new CertificateMaterial(
                testCertPem, testKeyPem);
        byte[] data = "deterministic-test".getBytes();
        SignedPayload first = service.sign(data, cert);
        SignedPayload second = service.sign(data, cert);
        assertArrayEquals(first.signature(), second.signature());
    }

    @Test
    void throwsWhenCertificateIsNull() {
        CertificateMaterial cert = new CertificateMaterial(null, "key");
        assertThrows(NoCertificateConfiguredException.class,
                () -> service.sign("data".getBytes(), cert));
    }

    @Test
    void throwsWhenCertificateIsEmpty() {
        CertificateMaterial cert = new CertificateMaterial("", "key");
        assertThrows(NoCertificateConfiguredException.class,
                () -> service.sign("data".getBytes(), cert));
    }

    @Test
    void throwsWhenPrivateKeyIsNull() {
        CertificateMaterial cert = new CertificateMaterial("cert", null);
        assertThrows(NoCertificateConfiguredException.class,
                () -> service.sign("data".getBytes(), cert));
    }
}
