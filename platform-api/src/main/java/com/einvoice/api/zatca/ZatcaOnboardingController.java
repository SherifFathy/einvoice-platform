package com.einvoice.api.zatca;

import com.einvoice.api.zatca.dto.CertificateStatusResponse;
import com.einvoice.api.zatca.dto.ImportCsidResponse;
import com.einvoice.api.zatca.dto.OnboardRequest;
import com.einvoice.api.zatca.dto.OnboardingStatusResponse;
import com.einvoice.api.zatca.dto.RenewCertificateRequest;
import com.einvoice.api.zatca.dto.RenewCertificateResponse;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.BranchRepository;
import com.einvoice.core.service.BranchService;
import com.einvoice.zatca.onboarding.ZatcaOnboardingService;
import com.einvoice.zatca.onboarding.ZatcaOnboardingService.CsrData;
import com.einvoice.zatca.renewal.ZatcaCertRenewalService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for ZATCA certificate onboarding and renewal. */
@RestController
@RequestMapping("/api/branches/{branchId}/zatca")
public class ZatcaOnboardingController {

    private final ZatcaOnboardingService onboardingService;
    private final ZatcaCertRenewalService certRenewalService;
    private final AuthorityConfigRepository authorityConfigRepository;
    private final BranchRepository branchRepository;

    /**
     * Creates the controller.
     *
     * @param onboardingService         ZATCA onboarding service
     * @param certRenewalService        ZATCA certificate renewal service
     * @param authorityConfigRepository authority configuration repository
     * @param branchRepository          branch repository
     */
    public ZatcaOnboardingController(ZatcaOnboardingService onboardingService,
            ZatcaCertRenewalService certRenewalService,
            AuthorityConfigRepository authorityConfigRepository,
            BranchRepository branchRepository) {
        this.onboardingService = onboardingService;
        this.certRenewalService = certRenewalService;
        this.authorityConfigRepository = authorityConfigRepository;
        this.branchRepository = branchRepository;
    }

    /**
     * Starts the ZATCA onboarding flow for a branch.
     *
     * @param branchId the branch identifier
     * @param request  the onboarding request body
     * @return the onboarding status response
     */
    @PostMapping("/onboard")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<OnboardingStatusResponse> onboard(
            @PathVariable Long branchId,
            @Valid @RequestBody OnboardRequest request) {
        validateBranchOwnership(branchId);
        validateEnvironment(request.environment());

        CsrData csrData = null;
        if (request.csrData() != null) {
            csrData = new CsrData(
                    request.csrData().commonName(),
                    request.csrData().organizationUnit(),
                    request.csrData().organization(),
                    request.csrData().country(),
                    request.csrData().serialNumber(),
                    request.csrData().otp());
        }

        ZatcaOnboardingService.OnboardingResult result =
                onboardingService.onboard(branchId, request.environment(), csrData);

        return ResponseEntity.ok(toStatusResponse(result));
    }

    /**
     * Returns the current onboarding status for a branch.
     *
     * @param branchId    the branch identifier
     * @param environment the ZATCA environment name
     * @return the onboarding status response
     */
    @GetMapping("/onboard/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<OnboardingStatusResponse> getOnboardingStatus(
            @PathVariable Long branchId,
            @RequestParam String environment) {
        validateBranchOwnership(branchId);
        validateEnvironment(environment);

        ZatcaOnboardingService.OnboardingResult result =
                onboardingService.getStatus(branchId, environment);

        return ResponseEntity.ok(toStatusResponse(result));
    }

    /**
     * Imports an externally-obtained CSID for a branch.
     *
     * @param branchId    the branch identifier
     * @param environment the ZATCA environment name
     * @param certificate the uploaded certificate file
     * @param privateKey  the uploaded private key file
     * @param csidSecret  the CSID secret string
     * @return the import CSID response
     */
    @PostMapping("/import-csid")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ImportCsidResponse> importCsid(
            @PathVariable Long branchId,
            @RequestParam String environment,
            @RequestParam("certificate") MultipartFile certificate,
            @RequestParam("privateKey") MultipartFile privateKey,
            @RequestParam("csidSecret") String csidSecret) {
        validateBranchOwnership(branchId);
        validateEnvironment(environment);

        byte[] certBytes;
        byte[] keyBytes;
        try {
            certBytes = certificate.getBytes();
            keyBytes = privateKey.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read uploaded files");
        }

        onboardingService.importCsid(branchId, environment, certBytes, keyBytes, csidSecret);

        AuthorityConfig config = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(
                        branchId, Authority.ZATCA, validateEnvironment(environment))
                .orElseThrow();

        return ResponseEntity.ok(new ImportCsidResponse(
                branchId,
                environment,
                config.getCertificateExpiryDate() != null
                        ? config.getCertificateExpiryDate().toString() : "unknown",
                "READY"));
    }

    /**
     * Renews the ZATCA certificate for a branch.
     *
     * @param branchId the branch identifier
     * @param request  the renewal request body
     * @return the renewal response
     */
    @PostMapping("/renew-certificate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<RenewCertificateResponse> renewCertificate(
            @PathVariable Long branchId,
            @Valid @RequestBody RenewCertificateRequest request) {
        validateBranchOwnership(branchId);
        validateEnvironment(request.environment());

        ZatcaCertRenewalService.RenewalResult result =
                certRenewalService.renewCertificate(branchId, request.environment());

        if (!result.success()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, result.error());
        }

        return ResponseEntity.ok(new RenewCertificateResponse(
                branchId,
                request.environment(),
                result.newExpiryDate(),
                "RENEWED"));
    }

    /**
     * Returns certificate expiry status for a branch.
     *
     * @param branchId    the branch identifier
     * @param environment the ZATCA environment name
     * @return the certificate status response
     */
    @GetMapping("/certificate-status")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<CertificateStatusResponse> getCertificateStatus(
            @PathVariable Long branchId,
            @RequestParam String environment) {
        validateBranchOwnership(branchId);

        Environment env = validateEnvironment(environment);
        AuthorityConfig config = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(branchId, Authority.ZATCA, env)
                .orElse(null);

        if (config == null) {
            return ResponseEntity.ok(new CertificateStatusResponse(
                    branchId, environment, false, null, 0, false, "NOT_STARTED"));
        }

        boolean hasCertificate = config.getCertificateEncrypted() != null;
        OffsetDateTime expiry = config.getCertificateExpiryDate();
        long daysUntilExpiry = expiry != null
                ? ChronoUnit.DAYS.between(java.time.LocalDate.now(),
                        expiry.toLocalDate()) : 0;
        boolean expiryWarning = hasCertificate && daysUntilExpiry >= 0 && daysUntilExpiry < 30;
        String onboardingStatus = config.getOnboardingStatus() != null
                ? config.getOnboardingStatus() : "NOT_STARTED";

        return ResponseEntity.ok(new CertificateStatusResponse(
                branchId, environment, hasCertificate, expiry,
                daysUntilExpiry, expiryWarning, onboardingStatus));
    }

    private void validateBranchOwnership(Long branchId) {
        Long companyId = TenantContext.getCurrentTenantId();
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BranchService.BranchNotFoundException(
                        "Branch not found: " + branchId));
        if (!branch.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Branch does not belong to current company");
        }
    }

    private Environment validateEnvironment(String environment) {
        try {
            return Environment.valueOf(environment);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid environment: " + environment
                            + ". Valid values: ZATCA_SANDBOX, ZATCA_SIMULATION, ZATCA_PRODUCTION");
        }
    }

    private OnboardingStatusResponse toStatusResponse(
            ZatcaOnboardingService.OnboardingResult result) {
        return new OnboardingStatusResponse(
                result.branchId(),
                result.currentStep().name(),
                result.completedSteps().stream().map(Enum::name).toList(),
                result.remainingSteps().stream().map(Enum::name).toList(),
                result.status(),
                result.message(),
                result.lastError(),
                result.startedAt(),
                result.completedAt());
    }
}
