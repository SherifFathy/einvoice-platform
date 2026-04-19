package com.einvoice.zatca.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.OnboardingProgress;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.OnboardingStep;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.OnboardingProgressRepository;
import com.einvoice.core.service.AuthorityConfigService;
import com.einvoice.core.service.CryptoService;
import com.einvoice.zatca.client.ZatcaComplianceClient;
import com.einvoice.zatca.client.ZatcaProductionCsidClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaOnboardingServiceTest {

    private ZatcaOnboardingService service;
    private ZatcaCsrGenerator csrGenerator;
    private ZatcaComplianceClient complianceClient;
    private ZatcaProductionCsidClient productionCsidClient;
    private OnboardingProgressRepository progressRepository;
    private AuthorityConfigRepository authorityConfigRepository;
    private AuthorityConfigService authorityConfigService;
    private CryptoService cryptoService;

    private static final Long BRANCH_ID = 1L;
    private static final Long COMPANY_ID = 100L;
    private static final Environment ENV = Environment.ZATCA_SANDBOX;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        csrGenerator = mock(ZatcaCsrGenerator.class);
        complianceClient = mock(ZatcaComplianceClient.class);
        productionCsidClient = mock(ZatcaProductionCsidClient.class);
        progressRepository = mock(OnboardingProgressRepository.class);
        authorityConfigRepository = mock(AuthorityConfigRepository.class);
        authorityConfigService = mock(AuthorityConfigService.class);
        cryptoService = mock(CryptoService.class);

        when(progressRepository.save(any(OnboardingProgress.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service = new ZatcaOnboardingService(
                csrGenerator, complianceClient, productionCsidClient,
                progressRepository, authorityConfigRepository,
                authorityConfigService, cryptoService, new ObjectMapper());
    }

    private AuthorityConfig buildConfig() {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(COMPANY_ID);
        Branch branch = mock(Branch.class);
        when(branch.getId()).thenReturn(BRANCH_ID);
        when(branch.getCompany()).thenReturn(company);

        AuthorityConfig config = mock(AuthorityConfig.class);
        when(config.getId()).thenReturn(1L);
        when(config.getBranch()).thenReturn(branch);
        when(config.getEnvironment()).thenReturn(ENV);
        when(config.getOnboardingStatus()).thenReturn(null);
        return config;
    }

    @Test
    void onboard_throwsWhenNoAuthorityConfig() {
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.empty());

        assertThrows(ZatcaOnboardingService.MissingAuthorityConfigException.class,
                () -> service.onboard(BRANCH_ID, "ZATCA_SANDBOX", null));
    }

    @Test
    void onboard_throwsWhenAlreadyCompleted() {
        AuthorityConfig config = buildConfig();
        when(config.getOnboardingStatus()).thenReturn("COMPLETED");
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.of(config));

        assertThrows(ZatcaOnboardingService.OnboardingAlreadyCompletedException.class,
                () -> service.onboard(BRANCH_ID, "ZATCA_SANDBOX",
                        new ZatcaOnboardingService.CsrData("CN", null, "O", "SA", "123", null)));
    }

    @Test
    void onboard_completesAllStepsSuccessfully() {
        AuthorityConfig config = buildConfig();
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.of(config));
        when(authorityConfigRepository.findById(1L)).thenReturn(Optional.of(config));

        when(progressRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.empty());

        ZatcaCsrGenerator.CsrResult csrResult = new ZatcaCsrGenerator.CsrResult("csrBase64", new byte[32]);
        when(csrGenerator.generateCsr(any(), any(), any(), any(), any())).thenReturn(csrResult);

        ZatcaComplianceClient.ComplianceCsidResult complianceResult =
                new ZatcaComplianceClient.ComplianceCsidResult(true, "csid", "secret", "req123", null);
        when(complianceClient.requestComplianceCsid(anyString(), any(), any())).thenReturn(complianceResult);

        ZatcaComplianceClient.TestInvoiceResult testResult =
                new ZatcaComplianceClient.TestInvoiceResult(true, null);
        when(complianceClient.submitTestInvoice(any(), any(), any(), any())).thenReturn(testResult);

        ZatcaProductionCsidClient.ProductionCsidResult prodResult =
                new ZatcaProductionCsidClient.ProductionCsidResult(true, "prodCsid", "prodSecret", null, null, null);
        when(productionCsidClient.exchangeForProductionCsid(anyString(), any())).thenReturn(prodResult);

        when(authorityConfigService.updateEncryptedFields(any(), any(), any(), any(), any(), any()))
                .thenReturn(config);

        ZatcaOnboardingService.OnboardingResult result = service.onboard(
                BRANCH_ID, "ZATCA_SANDBOX",
                new ZatcaOnboardingService.CsrData("CN", null, "O", "SA", "123", "otp123"));

        assertEquals("COMPLETED", result.status());
        assertEquals(OnboardingStep.PRODUCTION_CSID_OBTAINED, result.currentStep());
        assertEquals(0, result.remainingSteps().size());
        assertEquals(4, result.completedSteps().size());
    }

    @Test
    void onboard_persistsLastErrorOnStepFailure() {
        AuthorityConfig config = buildConfig();
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.of(config));

        OnboardingProgress progress = OnboardingProgress.builder()
                .branch(config.getBranch())
                .authority(Authority.ZATCA)
                .environment(ENV)
                .currentStep(OnboardingStep.COMPLIANCE_CSID_OBTAINED)
                .stepData("{\"csrBase64\":\"csrData\"}")
                .build();
        when(progressRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.of(progress));

        when(complianceClient.submitTestInvoice(any(), any(), any(), any()))
                .thenReturn(new ZatcaComplianceClient.TestInvoiceResult(false, "Invalid invoice"));

        ZatcaOnboardingService.OnboardingResult result = service.onboard(
                BRANCH_ID, "ZATCA_SANDBOX", null);

        assertEquals("FAILED", result.status());
        assertNotNull(result.lastError());
        assertTrue(result.lastError().contains("Invalid invoice"));
    }

    @Test
    void getStatus_returnsNotStartedWhenNoConfig() {
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.empty());

        ZatcaOnboardingService.OnboardingResult result = service.getStatus(BRANCH_ID, "ZATCA_SANDBOX");

        assertEquals("NOT_STARTED", result.status());
        assertEquals(OnboardingStep.NOT_STARTED, result.currentStep());
    }

    @Test
    void getStatus_propagatesLastError() {
        AuthorityConfig config = buildConfig();
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.of(config));

        OnboardingProgress progress = OnboardingProgress.builder()
                .branch(config.getBranch())
                .authority(Authority.ZATCA)
                .environment(ENV)
                .currentStep(OnboardingStep.CSR_GENERATED)
                .lastError("OTP expired")
                .build();
        when(progressRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.of(progress));

        ZatcaOnboardingService.OnboardingResult result = service.getStatus(BRANCH_ID, "ZATCA_SANDBOX");

        assertEquals("OTP expired", result.lastError());
    }

    @Test
    void importCsid_acceptsMatchingRsaKeyPair() throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair keyPair = kpg.generateKeyPair();

        String dn = "CN=Test,O=Org,C=SA";
        org.bouncycastle.asn1.x500.X500Name subject =
                new org.bouncycastle.asn1.x500.X500Name(dn);
        org.bouncycastle.cert.X509CertificateHolder certHolder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        subject, java.math.BigInteger.ONE,
                        java.util.Date.from(java.time.Instant.now().minusSeconds(3600)),
                        java.util.Date.from(java.time.Instant.now().plusSeconds(3600)),
                        subject, keyPair.getPublic())
                        .build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(
                                "SHA256WithRSA").build(keyPair.getPrivate()));
        java.security.cert.X509Certificate cert =
                new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                        .getCertificate(certHolder);
        final byte[] certBytes = cert.getEncoded();
        final byte[] keyBytes = keyPair.getPrivate().getEncoded();

        AuthorityConfig config = buildConfig();
        when(config.getId()).thenReturn(1L);
        when(config.getCertificateExpiryDate()).thenReturn(null);
        when(config.getOnboardingStatus()).thenReturn(null);
        when(authorityConfigService.getByBranchAuthorityEnv(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(config);
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(Optional.of(config));
        when(authorityConfigService.updateEncryptedFields(any(), any(), any(), any(), any(), any()))
                .thenReturn(config);

        service.importCsid(BRANCH_ID, "ZATCA_SANDBOX", certBytes, keyBytes, "secret123");
    }

    @Test
    void importCsid_rejectsMismatchedKeyPair() throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair keyPair1 = kpg.generateKeyPair();
        java.security.KeyPair keyPair2 = kpg.generateKeyPair();

        String dn = "CN=Test,O=Org,C=SA";
        org.bouncycastle.asn1.x500.X500Name subject =
                new org.bouncycastle.asn1.x500.X500Name(dn);
        org.bouncycastle.cert.X509CertificateHolder certHolder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        subject, java.math.BigInteger.ONE,
                        java.util.Date.from(java.time.Instant.now().minusSeconds(3600)),
                        java.util.Date.from(java.time.Instant.now().plusSeconds(3600)),
                        subject, keyPair1.getPublic())
                        .build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(
                                "SHA256WithRSA").build(keyPair1.getPrivate()));
        java.security.cert.X509Certificate cert =
                new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                        .getCertificate(certHolder);
        byte[] certBytes = cert.getEncoded();
        byte[] wrongKeyBytes = keyPair2.getPrivate().getEncoded();

        AuthorityConfig config = buildConfig();
        when(authorityConfigService.getByBranchAuthorityEnv(
                BRANCH_ID, Authority.ZATCA, ENV)).thenReturn(config);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importCsid(BRANCH_ID, "ZATCA_SANDBOX",
                        certBytes, wrongKeyBytes, "secret"));
        assertTrue(ex.getMessage().contains("does not match"));
    }
}
