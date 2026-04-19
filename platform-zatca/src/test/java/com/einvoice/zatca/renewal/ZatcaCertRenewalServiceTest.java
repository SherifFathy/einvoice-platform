package com.einvoice.zatca.renewal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.service.AuthorityConfigService;
import com.einvoice.core.service.CryptoService;
import com.einvoice.zatca.client.ZatcaProductionCsidClient;
import com.einvoice.zatca.onboarding.ZatcaCsrGenerator;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaCertRenewalServiceTest {

    private ZatcaCertRenewalService service;
    private ZatcaCsrGenerator csrGenerator;
    private ZatcaProductionCsidClient productionCsidClient;
    private AuthorityConfigService authorityConfigService;
    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        csrGenerator = mock(ZatcaCsrGenerator.class);
        productionCsidClient = mock(ZatcaProductionCsidClient.class);
        authorityConfigService = mock(AuthorityConfigService.class);
        cryptoService = mock(CryptoService.class);

        service = new ZatcaCertRenewalService(
                csrGenerator, productionCsidClient,
                authorityConfigService, cryptoService);
    }

    @Test
    void renewCertificate_throwsMissingCertificateWhenNoCsid() {
        AuthorityConfig config = mock(AuthorityConfig.class);
        when(config.getCsidEncrypted()).thenReturn(null);
        when(authorityConfigService.getByBranchAuthorityEnv(1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(config);

        assertThrows(ZatcaCertRenewalService.MissingCertificateException.class,
                () -> service.renewCertificate(1L, "ZATCA_SANDBOX"));
    }

    @Test
    void renewCertificate_throwsMissingCertificateWhenNoCert() {
        AuthorityConfig config = mock(AuthorityConfig.class);
        when(config.getCsidEncrypted()).thenReturn(new byte[10]);
        when(config.getCertificateEncrypted()).thenReturn(null);
        when(authorityConfigService.getByBranchAuthorityEnv(1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(config);

        assertThrows(ZatcaCertRenewalService.MissingCertificateException.class,
                () -> service.renewCertificate(1L, "ZATCA_SANDBOX"));
    }

    @Test
    void renewCertificate_returnsNewCertExpiryFromUpstream() throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair keyPair = kpg.generateKeyPair();

        String dn = "SERIALNUMBER=1234567890,CN=Test,O=TestOrg,C=SA";
        org.bouncycastle.asn1.x500.X500Name subject = new org.bouncycastle.asn1.x500.X500Name(dn);
        org.bouncycastle.cert.X509CertificateHolder certHolder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        subject, java.math.BigInteger.ONE,
                        java.util.Date.from(java.time.Instant.now().minus(java.time.Duration.ofDays(1))),
                        java.util.Date.from(java.time.Instant.now().plus(java.time.Duration.ofDays(365))),
                        subject, keyPair.getPublic())
                        .build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA")
                                .build(keyPair.getPrivate()));
        java.security.cert.X509Certificate cert =
                new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                        .getCertificate(certHolder);
        byte[] certBytes = cert.getEncoded();

        AuthorityConfig config = mock(AuthorityConfig.class);
        when(config.getId()).thenReturn(1L);
        when(config.getCsidEncrypted()).thenReturn(new byte[10]);
        when(config.getCertificateEncrypted()).thenReturn(new byte[10]);
        when(authorityConfigService.getByBranchAuthorityEnv(1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(config);

        when(cryptoService.decrypt(any(byte[].class))).thenReturn(certBytes);

        ZatcaCsrGenerator.CsrResult csrResult = new ZatcaCsrGenerator.CsrResult("csrBase64", new byte[32]);
        when(csrGenerator.generateCsr(any(), any(), any(), any(), any())).thenReturn(csrResult);

        OffsetDateTime expectedExpiry = OffsetDateTime.now().plusDays(180);
        ZatcaProductionCsidClient.RenewalResult renewalResult =
                new ZatcaProductionCsidClient.RenewalResult(
                        true, "newCsid", "newSecret", new byte[100], expectedExpiry, null);
        when(productionCsidClient.renewCertificate(anyString(), any())).thenReturn(renewalResult);

        when(authorityConfigService.updateEncryptedFields(any(), any(), any(), any(), any(), any()))
                .thenReturn(config);

        ZatcaCertRenewalService.RenewalResult result = service.renewCertificate(1L, "ZATCA_SANDBOX");

        assertTrue(result.success());
        assertEquals(expectedExpiry, result.newExpiryDate());
    }

    @Test
    void renewCertificate_failsWhenUpstreamFails() throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair keyPair = kpg.generateKeyPair();

        String dn = "SERIALNUMBER=1234567890,CN=Test,O=TestOrg,C=SA";
        org.bouncycastle.asn1.x500.X500Name subject = new org.bouncycastle.asn1.x500.X500Name(dn);
        org.bouncycastle.cert.X509CertificateHolder certHolder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        subject, java.math.BigInteger.ONE,
                        java.util.Date.from(java.time.Instant.now().minus(java.time.Duration.ofDays(1))),
                        java.util.Date.from(java.time.Instant.now().plus(java.time.Duration.ofDays(365))),
                        subject, keyPair.getPublic())
                        .build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA")
                                .build(keyPair.getPrivate()));
        java.security.cert.X509Certificate cert =
                new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                        .getCertificate(certHolder);

        AuthorityConfig config = mock(AuthorityConfig.class);
        when(config.getId()).thenReturn(1L);
        when(config.getCsidEncrypted()).thenReturn(new byte[10]);
        when(config.getCertificateEncrypted()).thenReturn(new byte[10]);
        when(authorityConfigService.getByBranchAuthorityEnv(1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(config);

        when(cryptoService.decrypt(any(byte[].class))).thenReturn(cert.getEncoded());

        ZatcaCsrGenerator.CsrResult csrResult = new ZatcaCsrGenerator.CsrResult("csrBase64", new byte[32]);
        when(csrGenerator.generateCsr(any(), any(), any(), any(), any())).thenReturn(csrResult);

        ZatcaProductionCsidClient.RenewalResult renewalResult =
                new ZatcaProductionCsidClient.RenewalResult(false, null, null, null, null, "HTTP 403");
        when(productionCsidClient.renewCertificate(anyString(), any())).thenReturn(renewalResult);

        ZatcaCertRenewalService.RenewalResult result = service.renewCertificate(1L, "ZATCA_SANDBOX");

        assertTrue(result.success() == false);
        assertEquals("HTTP 403", result.error());
    }
}
