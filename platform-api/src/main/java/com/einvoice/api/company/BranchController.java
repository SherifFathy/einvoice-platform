package com.einvoice.api.company;

import com.einvoice.api.company.dto.AuthorityConfigDetailResponse;
import com.einvoice.api.company.dto.AuthorityConfigRequest;
import com.einvoice.api.company.dto.BranchDetailResponse;
import com.einvoice.api.company.dto.BranchRequest;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.enums.InvoiceResetPolicy;
import com.einvoice.core.service.AuthorityConfigService;
import com.einvoice.core.service.BranchService;
import jakarta.validation.Valid;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for Company Admin branch and authority config management. */
@RestController
@RequestMapping("/api/companies/{companyId}")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class BranchController {

    private final BranchService branchService;
    private final AuthorityConfigService authorityConfigService;

    /**
     * Creates the controller.
     *
     * @param branchService the branch service
     * @param authorityConfigService the authority config service
     */
    public BranchController(BranchService branchService,
            AuthorityConfigService authorityConfigService) {
        this.branchService = branchService;
        this.authorityConfigService = authorityConfigService;
    }

    /**
     * Lists branches for a company.
     *
     * @param companyId the company identifier
     * @return paginated list of branches
     */
    @GetMapping("/branches")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<BranchDetailResponse>> listBranches(
            @PathVariable Long companyId) {
        validateTenantAccess(companyId);
        List<Branch> branches = branchService.listByCompany(companyId);
        List<BranchDetailResponse> response = branches.stream()
                .map(this::toBranchResponse).toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new branch.
     *
     * @param companyId the company identifier
     * @param request the branch creation request
     * @return the created branch
     */
    @PostMapping("/branches")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BranchDetailResponse> createBranch(
            @PathVariable Long companyId, @Valid @RequestBody BranchRequest request) {
        validateTenantAccess(companyId);
        Branch branch = branchService.create(companyId, request.nameAr(),
                request.nameEn(), request.branchCode(), request.street(),
                request.buildingNumber(), request.additionalNumber(),
                request.city(), request.district(), request.postalCode(),
                request.countryCode(), request.additionalStreet());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toBranchResponse(branch));
    }

    /**
     * Updates a branch.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @param request the branch update request
     * @return the updated branch
     */
    @PutMapping("/branches/{branchId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<BranchDetailResponse> updateBranch(
            @PathVariable Long companyId, @PathVariable Long branchId,
            @Valid @RequestBody BranchRequest request) {
        validateTenantAccess(companyId);
        validateBranchBelongsToCompany(branchId, companyId);
        Branch branch = branchService.update(branchId, request.nameAr(),
                request.nameEn(), request.branchCode(), request.street(),
                request.buildingNumber(), request.additionalNumber(),
                request.city(), request.district(), request.postalCode(),
                request.countryCode(), request.additionalStreet());
        return ResponseEntity.ok(toBranchResponse(branch));
    }

    /**
     * Deactivates a branch.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @return no content
     */
    @DeleteMapping("/branches/{branchId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deactivateBranch(
            @PathVariable Long companyId, @PathVariable Long branchId) {
        validateTenantAccess(companyId);
        validateBranchBelongsToCompany(branchId, companyId);
        branchService.deactivate(branchId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists authority configurations for a branch.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @return list of authority configs
     */
    @GetMapping("/branches/{branchId}/authority-configs")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<AuthorityConfigDetailResponse>> listAuthorityConfigs(
            @PathVariable Long companyId, @PathVariable Long branchId) {
        validateTenantAccess(companyId);
        validateBranchBelongsToCompany(branchId, companyId);
        List<AuthorityConfig> configs = authorityConfigService.listByBranch(branchId);
        List<AuthorityConfigDetailResponse> response = configs.stream()
                .map(this::toAuthorityConfigResponse).toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Creates or updates an authority configuration.
     * Decodes base64-encoded credential fields and persists them via
     * {@link AuthorityConfigService#updateEncryptedFields}.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @param request the authority config request
     * @return the authority config
     */
    @PutMapping("/branches/{branchId}/authority-configs")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AuthorityConfigDetailResponse> upsertAuthorityConfig(
            @PathVariable Long companyId, @PathVariable Long branchId,
            @Valid @RequestBody AuthorityConfigRequest request) {
        validateTenantAccess(companyId);
        validateBranchBelongsToCompany(branchId, companyId);

        InvoiceResetPolicy resetPolicy = request.invoiceResetPolicy() != null
                ? InvoiceResetPolicy.valueOf(request.invoiceResetPolicy()) : null;

        AuthorityConfig config;
        try {
            config = authorityConfigService.getByBranchAuthorityEnv(
                    branchId, request.authority(), request.environment());

            if (request.invoicePrefix() != null || request.invoiceStartingNumber() != null
                    || resetPolicy != null) {
                config = authorityConfigService.updateInvoiceSettings(config.getId(),
                        request.invoicePrefix(), request.invoiceStartingNumber(), resetPolicy);
            }
        } catch (AuthorityConfigService.AuthorityConfigNotFoundException e) {
            config = authorityConfigService.create(branchId, request.authority(),
                    request.environment(), request.invoicePrefix(),
                    request.invoiceStartingNumber(), resetPolicy);
        }

        if (request.credentials() != null || request.certificate() != null
                || request.privateKey() != null) {
            byte[] credentialsBytes = decodeBase64(request.credentials());
            byte[] certificateBytes = decodeBase64(request.certificate());
            byte[] privateKeyBytes = decodeBase64(request.privateKey());
            config = authorityConfigService.updateEncryptedFields(
                    config.getId(), credentialsBytes, certificateBytes,
                    null, privateKeyBytes, null);
        }

        return ResponseEntity.ok(toAuthorityConfigResponse(config));
    }

    private void validateTenantAccess(Long companyId) {
        boolean isSuperAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (isSuperAdmin) {
            return;
        }
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null && !tenantId.equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot access company " + companyId + " from current tenant context");
        }
    }

    private void validateBranchBelongsToCompany(Long branchId, Long companyId) {
        Branch branch = branchService.getById(branchId);
        if (!branch.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Branch " + branchId + " does not belong to company " + companyId);
        }
    }

    private byte[] decodeBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        return Base64.getDecoder().decode(base64);
    }

    private BranchDetailResponse toBranchResponse(Branch b) {
        return new BranchDetailResponse(b.getId(), b.getCompany().getId(),
                b.getNameAr(), b.getNameEn(), b.getBranchCode(),
                b.getStreet(), b.getBuildingNumber(), b.getAdditionalNumber(),
                b.getCity(), b.getDistrict(), b.getPostalCode(),
                b.getCountryCode(), b.getAdditionalStreet(),
                b.getIsActive(), b.getCreatedAt());
    }

    private AuthorityConfigDetailResponse toAuthorityConfigResponse(AuthorityConfig c) {
        return new AuthorityConfigDetailResponse(c.getId(), c.getBranch().getId(),
                c.getAuthority(), c.getEnvironment(),
                c.getCredentialsEncrypted() != null,
                c.getCertificateEncrypted() != null,
                c.getCertificateExpiryDate() != null
                        ? c.getCertificateExpiryDate().toString() : null,
                c.getInvoiceCounter(), c.getInvoicePrefix(),
                c.getInvoiceStartingNumber(),
                c.getInvoiceResetPolicy() != null ? c.getInvoiceResetPolicy().name() : null,
                c.getIsActive());
    }
}
