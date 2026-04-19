package com.einvoice.zatca.signing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaSigningServiceTest {

    private ZatcaSigningService signingService;
    private X509Certificate testCertificate;
    private java.security.PrivateKey testPrivateKey;

    @BeforeEach
    void setUp() throws Exception {
        signingService = new ZatcaSigningService();

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        testPrivateKey = keyPair.getPrivate();

        X500Name issuer = new X500Name("CN=Test,O=TestOrg,C=SA");
        java.math.BigInteger serial = java.math.BigInteger.ONE;
        java.util.Date notBefore = new java.util.Date();
        java.util.Date notAfter = new java.util.Date(
                System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
        X500Name subject = new X500Name("CN=Test,O=TestOrg,C=SA");

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());
        X509CertificateHolder certHolder = certBuilder.build(
                new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                        .build(keyPair.getPrivate()));
        testCertificate = new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    @Test
    void shouldSignXmlDocument() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cbc:ID>TEST-001</cbc:ID>"
                + "</Invoice>";

        ZatcaSigningService.SigningResult result =
                signingService.sign(xml, testCertificate, testPrivateKey);
        String signedXml = result.signedXml();

        assertNotNull(signedXml);
        assertTrue(signedXml.contains("Signature"));
        assertTrue(signedXml.contains("SignedInfo"));
        assertTrue(signedXml.contains("SignatureValue"));
    }

    @Test
    void shouldContainKeyInfo() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cbc:ID>TEST-002</cbc:ID>"
                + "</Invoice>";

        ZatcaSigningService.SigningResult result =
                signingService.sign(xml, testCertificate, testPrivateKey);
        String signedXml = result.signedXml();

        assertTrue(signedXml.contains("KeyInfo"));
        assertTrue(signedXml.contains("X509Data"));
        assertTrue(signedXml.contains("X509Certificate"));
    }

    @Test
    void shouldPreserveOriginalContent() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cbc:ID>TEST-003</cbc:ID>"
                + "</Invoice>";

        ZatcaSigningService.SigningResult result =
                signingService.sign(xml, testCertificate, testPrivateKey);
        String signedXml = result.signedXml();

        assertTrue(signedXml.contains("TEST-003"));
    }

    @Test
    void shouldContainReferenceElement() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cbc:ID>TEST-004</cbc:ID>"
                + "</Invoice>";

        ZatcaSigningService.SigningResult result =
                signingService.sign(xml, testCertificate, testPrivateKey);
        String signedXml = result.signedXml();

        assertTrue(signedXml.contains("Reference"));
        assertTrue(signedXml.contains("DigestValue"));
    }

    @Test
    void shouldProduceXadesBesStructure() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cbc:ID>XADES-TEST</cbc:ID>"
                + "</Invoice>";
        ZatcaSigningService.SigningResult result =
                signingService.sign(xml, testCertificate, testPrivateKey);
        String signedXml = result.signedXml();
        assertTrue(signedXml.contains("QualifyingProperties"),
                "XAdES-BES requires xades:QualifyingProperties");
        assertTrue(signedXml.contains("SignedProperties"),
                "XAdES-BES requires xades:SignedProperties");
        assertTrue(signedXml.contains("SigningCertificate")
                        || signedXml.contains("SigningCertificateV2"),
                "XAdES-BES requires a signing certificate property");
    }

    @Test
    void shouldExcludeUblExtensionsFromDigest() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cbc:ID>XPATH-TEST</cbc:ID>"
                + "</Invoice>";
        ZatcaSigningService.SigningResult result =
                signingService.sign(xml, testCertificate, testPrivateKey);
        String signedXml = result.signedXml();
        assertTrue(signedXml.contains("UBLExtensions"),
                "UBLExtensions must be present after signing");
        assertTrue(signedXml.contains("Transform")
                        && signedXml.contains("xpath"),
                "XPath transform must be present to exclude UBLExtensions");
    }

    @Test
    void shouldExtractNonEmptySignatureValue() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cbc:ID>SIGVAL-TEST</cbc:ID>"
                + "</Invoice>";
        ZatcaSigningService.SigningResult result =
                signingService.sign(xml, testCertificate, testPrivateKey);
        assertNotNull(result.signatureValueBase64());
        assertFalse(result.signatureValueBase64().isBlank());
    }
}
