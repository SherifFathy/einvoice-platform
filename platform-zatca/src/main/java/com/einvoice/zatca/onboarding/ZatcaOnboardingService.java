package com.einvoice.zatca.onboarding;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.AuthorityConfig;
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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates the full ZATCA device onboarding lifecycle from CSR to production CSID. */
@Service
public class ZatcaOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(ZatcaOnboardingService.class);

    private static final List<OnboardingStep> ALL_STEPS = List.of(
            OnboardingStep.CSR_GENERATED,
            OnboardingStep.COMPLIANCE_CSID_OBTAINED,
            OnboardingStep.TEST_INVOICES_SUBMITTED,
            OnboardingStep.PRODUCTION_CSID_OBTAINED);

    private static final String[][] TEST_INVOICE_TYPES = {
            {"388", "0200000"}, {"388", "0200000"},
            {"381", "0200000"}, {"383", "0200000"},
            {"388", "0100000"}, {"388", "0200000"},
    };

    private final ZatcaCsrGenerator csrGenerator;
    private final ZatcaComplianceClient complianceClient;
    private final ZatcaProductionCsidClient productionCsidClient;
    private final OnboardingProgressRepository progressRepository;
    private final AuthorityConfigRepository authorityConfigRepository;
    private final AuthorityConfigService authorityConfigService;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    /**
     * Creates the onboarding service with its required collaborators.
     *
     * @param csrGenerator generates CSRs
     * @param complianceClient ZATCA compliance HTTP client
     * @param productionCsidClient ZATCA production CSID HTTP client
     * @param progressRepository onboarding progress repository
     * @param authorityConfigRepository authority config repository
     * @param authorityConfigService authority config service
     * @param cryptoService the crypto service
     * @param objectMapper JSON object mapper
     */
    public ZatcaOnboardingService(ZatcaCsrGenerator csrGenerator,
            ZatcaComplianceClient complianceClient,
            ZatcaProductionCsidClient productionCsidClient,
            OnboardingProgressRepository progressRepository,
            AuthorityConfigRepository authorityConfigRepository,
            AuthorityConfigService authorityConfigService,
            CryptoService cryptoService,
            ObjectMapper objectMapper) {
        this.csrGenerator = csrGenerator;
        this.complianceClient = complianceClient;
        this.productionCsidClient = productionCsidClient;
        this.progressRepository = progressRepository;
        this.authorityConfigRepository = authorityConfigRepository;
        this.authorityConfigService = authorityConfigService;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    /** Thrown when onboarding is attempted on an already-completed branch. */
    public static class OnboardingAlreadyCompletedException extends RuntimeException {
        /** @param message the detail message */
        public OnboardingAlreadyCompletedException(String message) {
            super(message);
        }
    }

    /** Thrown when the authority configuration is missing for the given branch. */
    public static class MissingAuthorityConfigException extends RuntimeException {
        /** @param message the detail message */
        public MissingAuthorityConfigException(String message) {
            super(message);
        }
    }

    public record OnboardingResult(Long branchId, OnboardingStep currentStep,
            List<OnboardingStep> completedSteps, List<OnboardingStep> remainingSteps,
            String status, String message, OffsetDateTime startedAt,
            OffsetDateTime completedAt, String lastError) {}

    public record CsrData(String commonName, String organizationUnit,
            String organization, String country, String serialNumber, String otp) {}

    /**
     * Runs the full ZATCA onboarding sequence for a branch from CSR through production CSID.
     *
     * @param branchId the branch to onboard
     * @param environment the target environment name
     * @param csrData CSR details and OTP for the first step
     * @return the onboarding result with current step and status
     */
    @Transactional
    @Audited(action = "zatca.onboard", entityType = "OnboardingProgress")
    public OnboardingResult onboard(Long branchId, String environment, CsrData csrData) {
        Environment env = Environment.valueOf(environment);

        AuthorityConfig config = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(branchId, Authority.ZATCA, env)
                .orElseThrow(() -> new MissingAuthorityConfigException(
                        "Authority config not found for branch " + branchId
                                + ". Create one via /authority-config first."));

        if ("COMPLETED".equals(config.getOnboardingStatus())) {
            throw new OnboardingAlreadyCompletedException(
                    "Onboarding already completed for branch " + branchId
                            + " environment " + environment);
        }

        OnboardingProgress progress = progressRepository
                .findByBranchIdAndAuthorityAndEnvironment(branchId, Authority.ZATCA, env)
                .orElseGet(() -> {
                    OnboardingProgress p = OnboardingProgress.builder()
                            .branch(config.getBranch())
                            .authority(Authority.ZATCA)
                            .environment(env)
                            .currentStep(OnboardingStep.NOT_STARTED)
                            .build();
                    p.setStartedAt(OffsetDateTime.now());
                    return p;
                });

        AuthorityConfig mutableConfig = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(branchId, Authority.ZATCA, env)
                .orElseThrow();

        while (progress.getCurrentStep() != OnboardingStep.PRODUCTION_CSID_OBTAINED) {
            try {
                progress = executeStep(progress, mutableConfig, csrData);
                progressRepository.save(progress);
                csrData = null;
                mutableConfig = authorityConfigRepository
                        .findByBranchIdAndAuthorityAndEnvironment(branchId, Authority.ZATCA, env)
                        .orElseThrow();
            } catch (OnboardingStepException e) {
                progress.setLastError(e.getMessage());
                progressRepository.save(progress);
                return buildResult(branchId, progress.getCurrentStep(),
                        "FAILED", "Step failed: " + e.getMessage(),
                        progress.getStartedAt(), null, e.getMessage());
            }
        }

        config.setOnboardingStatus("COMPLETED");
        progress.setCompletedAt(OffsetDateTime.now());
        authorityConfigRepository.save(config);
        progressRepository.save(progress);

        return buildResult(branchId, OnboardingStep.PRODUCTION_CSID_OBTAINED,
                "COMPLETED", "Onboarding complete. Branch is ready for ZATCA submission.",
                progress.getStartedAt(), progress.getCompletedAt(), null);
    }

    /**
     * Returns the current onboarding status for a branch.
     *
     * @param branchId the branch identifier
     * @param environment the target environment name
     * @return the current onboarding result
     */
    @Transactional(readOnly = true)
    public OnboardingResult getStatus(Long branchId, String environment) {
        Environment env = Environment.valueOf(environment);
        AuthorityConfig config = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(branchId, Authority.ZATCA, env)
                .orElse(null);

        if (config == null) {
            return buildResult(branchId, OnboardingStep.NOT_STARTED,
                    "NOT_STARTED", "No onboarding initiated.", null, null, null);
        }

        OnboardingProgress progress = progressRepository
                .findByBranchIdAndAuthorityAndEnvironment(branchId, Authority.ZATCA, env)
                .orElse(null);

        if (progress == null) {
            return buildResult(branchId, OnboardingStep.NOT_STARTED,
                    "NOT_STARTED", "No onboarding initiated.", null, null, null);
        }

        String status = "COMPLETED".equals(config.getOnboardingStatus())
                ? "COMPLETED" : "IN_PROGRESS";
        String message = progress.getLastError() != null
                ? progress.getLastError() : "Onboarding in progress.";

        return buildResult(branchId, progress.getCurrentStep(), status, message,
                progress.getStartedAt(), progress.getCompletedAt(), progress.getLastError());
    }

    /**
     * Imports an externally obtained CSID and certificate, marking onboarding as complete.
     *
     * @param branchId the branch identifier
     * @param environment the target environment name
     * @param certificateBytes the DER-encoded certificate bytes
     * @param privateKeyBytes the private key bytes (PEM or DER)
     * @param csidSecret the CSID secret credential
     */
    @Transactional
    @Audited(action = "zatca.importCsid", entityType = "AuthorityConfig")
    public void importCsid(Long branchId, String environment,
            byte[] certificateBytes, byte[] privateKeyBytes, String csidSecret) {
        Environment env = Environment.valueOf(environment);
        AuthorityConfig config = authorityConfigService.getByBranchAuthorityEnv(
                branchId, Authority.ZATCA, env);

        X509Certificate cert = parseCertificate(certificateBytes);
        PrivateKey privateKey = parsePrivateKey(privateKeyBytes);

        if (privateKey instanceof java.security.interfaces.RSAPrivateKey rsaPriv
                && cert.getPublicKey() instanceof java.security.interfaces.RSAPublicKey rsaPub) {
            if (!rsaPriv.getModulus().equals(rsaPub.getModulus())) {
                throw new IllegalArgumentException(
                        "Private key does not match the certificate's public key");
            }
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm (expected RSA)");
        }

        authorityConfigService.updateEncryptedFields(config.getId(),
                csidSecret.getBytes(StandardCharsets.UTF_8),
                certificateBytes,
                "imported".getBytes(StandardCharsets.UTF_8),
                privateKeyBytes,
                null);

        config.setCertificateExpiryDate(cert.getNotAfter().toInstant()
                .atZone(java.time.ZoneOffset.UTC).toOffsetDateTime());
        config.setOnboardingStatus("COMPLETED");
        authorityConfigRepository.save(config);

        OnboardingProgress progress = progressRepository
                .findByBranchIdAndAuthorityAndEnvironment(branchId, Authority.ZATCA, env)
                .orElseGet(() -> OnboardingProgress.builder()
                        .branch(config.getBranch())
                        .authority(Authority.ZATCA)
                        .environment(env)
                        .build());
        progress.setCurrentStep(OnboardingStep.PRODUCTION_CSID_OBTAINED);
        progress.setCompletedAt(OffsetDateTime.now());
        progressRepository.save(progress);

        log.info("CSID imported manually for branch {} environment {}", branchId, environment);
    }

    private OnboardingProgress executeStep(OnboardingProgress progress,
            AuthorityConfig config, CsrData csrData) {
        OnboardingStep current = progress.getCurrentStep();

        if (current == OnboardingStep.NOT_STARTED) {
            return executeCsrStep(progress, config, csrData);
        }
        if (current == OnboardingStep.CSR_GENERATED) {
            return executeComplianceCsidStep(progress, config, csrData);
        }
        if (current == OnboardingStep.COMPLIANCE_CSID_OBTAINED) {
            return executeTestInvoicesStep(progress, config);
        }
        if (current == OnboardingStep.TEST_INVOICES_SUBMITTED) {
            return executeProductionCsidStep(progress, config);
        }

        throw new OnboardingStepException("No further steps to execute. Current: " + current);
    }

    private OnboardingProgress executeCsrStep(OnboardingProgress progress,
            AuthorityConfig config, CsrData csrData) {
        if (csrData == null) {
            throw new OnboardingStepException("CSR data is required for the first step");
        }

        ZatcaCsrGenerator.CsrResult csrResult = csrGenerator.generateCsr(
                csrData.commonName(), csrData.organizationUnit(),
                csrData.organization(), csrData.country(), csrData.serialNumber());

        authorityConfigService.updateEncryptedFields(config.getId(),
                null, null, null, csrResult.privateKeyDer(), null);

        progress.setCurrentStep(OnboardingStep.CSR_GENERATED);
        Map<String, String> stepData = new HashMap<>();
        stepData.put("csrBase64", csrResult.csrBase64());
        if (csrData.otp() != null) {
            stepData.put("otp", csrData.otp());
        }
        progress.setStepData(writeStepData(stepData));

        log.info("CSR generated for branch {} env {}", config.getBranch().getId(), config.getEnvironment());
        return progress;
    }

    private OnboardingProgress executeComplianceCsidStep(OnboardingProgress progress,
            AuthorityConfig config, CsrData csrData) {
        String csrBase64 = readStepData(progress.getStepData(), "csrBase64");
        if (csrBase64 == null) {
            throw new OnboardingStepException("CSR not found in step data. Restart onboarding.");
        }

        String otp = readStepData(progress.getStepData(), "otp");
        if (otp == null && csrData != null) {
            otp = csrData.otp();
        }

        ZatcaComplianceClient.ComplianceCsidResult result =
                complianceClient.requestComplianceCsid(csrBase64, otp, config);

        if (!result.success()) {
            throw new OnboardingStepException(
                    "Failed to obtain compliance CSID: " + result.error());
        }

        authorityConfigService.updateEncryptedFields(config.getId(),
                result.secret() != null ? result.secret().getBytes(StandardCharsets.UTF_8) : null,
                null,
                result.csid() != null ? result.csid().getBytes(StandardCharsets.UTF_8) : null,
                null, null);

        config = authorityConfigRepository.findById(config.getId()).orElseThrow();

        progress.setCurrentStep(OnboardingStep.COMPLIANCE_CSID_OBTAINED);
        Map<String, String> existingData = readStepDataMap(progress.getStepData());
        existingData.put("complianceRequestId", result.requestId() != null ? result.requestId() : "");
        existingData.remove("otp");
        progress.setStepData(writeStepData(existingData));

        log.info("Compliance CSID obtained for branch {} env {}",
                config.getBranch().getId(), config.getEnvironment());
        return progress;
    }

    private OnboardingProgress executeTestInvoicesStep(OnboardingProgress progress,
            AuthorityConfig config) {
        config = authorityConfigRepository.findById(config.getId()).orElseThrow();

        for (int i = 0; i < TEST_INVOICE_TYPES.length; i++) {
            String invoiceTypeCode = TEST_INVOICE_TYPES[i][0];
            String invoiceType = TEST_INVOICE_TYPES[i][1];
            String uuid = UUID.randomUUID().toString();

            String signedInvoiceBase64 = buildComplianceTestInvoice(
                    uuid, invoiceTypeCode, invoiceType, config);
            String invoiceHash = computeInvoiceHash(signedInvoiceBase64);

            ZatcaComplianceClient.TestInvoiceResult result =
                    complianceClient.submitTestInvoice(
                            signedInvoiceBase64, invoiceHash, uuid, config);

            if (!result.success()) {
                throw new OnboardingStepException(
                        "Test invoice " + (i + 1) + "/" + TEST_INVOICE_TYPES.length
                                + " failed: " + result.error());
            }

            log.info("Test invoice {}/{} submitted for branch {}",
                    i + 1, TEST_INVOICE_TYPES.length, config.getBranch().getId());
        }

        progress.setCurrentStep(OnboardingStep.TEST_INVOICES_SUBMITTED);
        log.info("All test invoices submitted for branch {} env {}",
                config.getBranch().getId(), config.getEnvironment());
        return progress;
    }

    private OnboardingProgress executeProductionCsidStep(OnboardingProgress progress,
            AuthorityConfig config) {
        String requestId = readStepData(progress.getStepData(), "complianceRequestId");
        if (requestId == null || requestId.isBlank()) {
            throw new OnboardingStepException(
                    "Compliance request ID not found. Restart from compliance step.");
        }

        config = authorityConfigRepository.findById(config.getId()).orElseThrow();

        ZatcaProductionCsidClient.ProductionCsidResult result =
                productionCsidClient.exchangeForProductionCsid(requestId, config);

        if (!result.success()) {
            throw new OnboardingStepException(
                    "Failed to obtain production CSID: " + result.error());
        }

        byte[] certBytes = result.certificateBytes();
        authorityConfigService.updateEncryptedFields(config.getId(),
                result.secret() != null ? result.secret().getBytes(StandardCharsets.UTF_8) : null,
                certBytes,
                result.csid() != null ? result.csid().getBytes(StandardCharsets.UTF_8) : null,
                null, null);

        if (result.certificateExpiry() != null) {
            config = authorityConfigRepository.findById(config.getId()).orElseThrow();
            config.setCertificateExpiryDate(result.certificateExpiry());
            authorityConfigRepository.save(config);
        }

        progress.setCurrentStep(OnboardingStep.PRODUCTION_CSID_OBTAINED);
        progress.setCompletedAt(OffsetDateTime.now());

        log.info("Production CSID obtained for branch {} env {}",
                config.getBranch().getId(), config.getEnvironment());
        return progress;
    }

    private String buildComplianceTestInvoice(String uuid, String invoiceTypeCode,
            String invoiceType, AuthorityConfig config) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\">"
                + "<cbc:UUID>" + uuid + "</cbc:UUID>"
                + "<cbc:ID>TEST-" + invoiceTypeCode + "</cbc:ID>"
                + "<cbc:InvoiceTypeCode>" + invoiceTypeCode + "</cbc:InvoiceTypeCode>"
                + "<cbc:DocumentCurrencyCode>SAR</cbc:DocumentCurrencyCode>"
                + "<cbc:IssueDate>" + java.time.LocalDate.now() + "</cbc:IssueDate>"
                + "<cac:AccountingSupplierParty>"
                + "<cac:Party><cac:PartyIdentification>"
                + "<cbc:ID schemeID=\"CRN\">TEST</cbc:ID>"
                + "</cac:PartyIdentification></cac:Party>"
                + "</cac:AccountingSupplierParty>"
                + "<cac:InvoiceLine>"
                + "<cbc:ID>1</cbc:ID>"
                + "<cbc:InvoicedQuantity unitCode=\"PCE\">1</cbc:InvoicedQuantity>"
                + "<cbc:LineExtensionAmount currencyID=\"SAR\">100.00</cbc:LineExtensionAmount>"
                + "<cac:TaxTotal>"
                + "<cbc:TaxAmount currencyID=\"SAR\">15.00</cbc:TaxAmount>"
                + "<cac:TaxSubtotal>"
                + "<cbc:TaxableAmount currencyID=\"SAR\">100.00</cbc:TaxableAmount>"
                + "<cbc:TaxAmount currencyID=\"SAR\">15.00</cbc:TaxAmount>"
                + "<cac:TaxCategory>"
                + "<cbc:ID>S</cbc:ID>"
                + "<cbc:Percent>15.00</cbc:Percent>"
                + "<cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>"
                + "</cac:TaxCategory>"
                + "</cac:TaxSubtotal>"
                + "</cac:TaxTotal>"
                + "</cac:InvoiceLine>"
                + "</Invoice>";
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }

    private String computeInvoiceHash(String base64Invoice) {
        byte[] xmlBytes = Base64.getDecoder().decode(base64Invoice);
        try {
            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(xmlBytes);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return Base64.getEncoder().encodeToString(
                    UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private X509Certificate parseCertificate(byte[] certBytes) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(certBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid certificate format: " + e.getMessage(), e);
        }
    }

    private PrivateKey parsePrivateKey(byte[] keyBytes) {
        String pemContent = new String(keyBytes, StandardCharsets.UTF_8).trim();
        byte[] derBytes;
        if (pemContent.startsWith("-----BEGIN")) {
            String base64 = pemContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            derBytes = Base64.getDecoder().decode(base64);
        } else {
            derBytes = keyBytes;
        }
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(derBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid private key format: " + e.getMessage(), e);
        }
    }

    private OnboardingResult buildResult(Long branchId, OnboardingStep currentStep,
            String status, String message,
            OffsetDateTime startedAt, OffsetDateTime completedAt, String lastError) {
        List<OnboardingStep> completedSteps = new ArrayList<>();
        List<OnboardingStep> remainingSteps = new ArrayList<>();
        int stepIndex = ALL_STEPS.indexOf(currentStep);

        if (currentStep == OnboardingStep.NOT_STARTED) {
            remainingSteps.addAll(ALL_STEPS);
        } else if (currentStep == OnboardingStep.PRODUCTION_CSID_OBTAINED) {
            completedSteps.addAll(ALL_STEPS);
        } else {
            for (int i = 0; i <= stepIndex; i++) {
                completedSteps.add(ALL_STEPS.get(i));
            }
            for (int i = stepIndex + 1; i < ALL_STEPS.size(); i++) {
                remainingSteps.add(ALL_STEPS.get(i));
            }
        }

        return new OnboardingResult(branchId, currentStep, completedSteps,
                remainingSteps, status, message, startedAt, completedAt, lastError);
    }

    private String readStepData(String stepDataJson, String key) {
        if (stepDataJson == null || stepDataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(stepDataJson).path(key).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> readStepDataMap(String stepDataJson) {
        if (stepDataJson == null || stepDataJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(stepDataJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeStepData(Map<String, ?> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static class OnboardingStepException extends RuntimeException {
        OnboardingStepException(String message) {
            super(message);
        }
    }
}
