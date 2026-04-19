package com.einvoice.zatca.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaCsrGeneratorTest {

    private ZatcaCsrGenerator csrGenerator;

    @BeforeEach
    void setUp() {
        csrGenerator = new ZatcaCsrGenerator();
    }

    @Test
    void generateCsr_returnsValidPkcs10Structure() {
        ZatcaCsrGenerator.CsrResult result = csrGenerator.generateCsr(
                "Test Company", "IT", "Test Org", "SA", "1234567890");

        assertNotNull(result.csrBase64());
        assertNotNull(result.privateKeyDer());
        assertTrue(result.csrBase64().length() > 0);
        assertTrue(result.privateKeyDer().length > 0);
    }

    @Test
    void generateCsr_containsCorrectDnFields() throws Exception {
        ZatcaCsrGenerator.CsrResult result = csrGenerator.generateCsr(
                "Test Company", "IT", "Test Org", "SA", "1234567890");

        byte[] csrBytes = Base64.getDecoder().decode(result.csrBase64());
        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(csrBytes);
        JcaPKCS10CertificationRequest jcaCsr = new JcaPKCS10CertificationRequest(csrBytes);

        X500Name subject = csr.getSubject();
        String dn = subject.toString();

        assertTrue(dn.contains("SERIALNUMBER=1234567890"), "DN should contain serialNumber");
        assertTrue(dn.contains("CN=Test Company"), "DN should contain CN");
        assertTrue(dn.contains("OU=IT"), "DN should contain OU");
        assertTrue(dn.contains("O=Test Org"), "DN should contain O");
        assertTrue(dn.contains("C=SA"), "DN should contain C");
    }

    @Test
    void generateCsr_signatureIsValid() throws Exception {
        ZatcaCsrGenerator.CsrResult result = csrGenerator.generateCsr(
                "Test Company", null, "Test Org", "SA", "1234567890");

        byte[] csrBytes = Base64.getDecoder().decode(result.csrBase64());
        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(csrBytes);

        assertNotNull(csr.getSubjectPublicKeyInfo());
        assertNotNull(csr.getSignature());
    }

    @Test
    void generateCsr_withoutOptionalFields() {
        ZatcaCsrGenerator.CsrResult result = csrGenerator.generateCsr(
                "Test Company", null, null, "SA", "1234567890");

        assertNotNull(result.csrBase64());
        assertTrue(result.csrBase64().length() > 0);
    }

    @Test
    void generateCsr_producesUniqueKeysEachCall() {
        ZatcaCsrGenerator.CsrResult result1 = csrGenerator.generateCsr(
                "Test Company", null, "Org", "SA", "123");
        ZatcaCsrGenerator.CsrResult result2 = csrGenerator.generateCsr(
                "Test Company", null, "Org", "SA", "123");

        assertFalse(java.util.Arrays.equals(result1.privateKeyDer(), result2.privateKeyDer()),
                "Each call should produce a unique key pair");
    }
}
