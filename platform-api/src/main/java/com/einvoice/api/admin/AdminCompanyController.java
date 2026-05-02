package com.einvoice.api.admin;

import com.einvoice.api.admin.dto.AuthorityConfigResponse;
import com.einvoice.api.admin.dto.BranchResponse;
import com.einvoice.api.admin.dto.CompanyResponse;
import com.einvoice.api.admin.dto.CreateAuthorityConfigRequest;
import com.einvoice.api.admin.dto.CreateBranchRequest;
import com.einvoice.api.admin.dto.CreateCompanyRequest;
import com.einvoice.api.admin.dto.UpdateAuthorityConfigRequest;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.service.AuthorityConfigService;
import com.einvoice.core.service.BranchService;
import com.einvoice.core.service.CompanyService;
import com.einvoice.core.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for Super Admin company onboarding and management endpoints. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCompanyController {

    private final CompanyService companyService;
    private final BranchService branchService;
    private final AuthorityConfigService authorityConfigService;
    private final UserService userService;

    /**
     * Creates the admin company controller.
     *
     * @param companyService the company service
     * @param branchService the branch service
     * @param authorityConfigService the authority config service
     * @param userService the user service
     */
    public AdminCompanyController(CompanyService companyService,
            BranchService branchService, AuthorityConfigService authorityConfigService,
            UserService userService) {
        this.companyService = companyService;
        this.branchService = branchService;
        this.authorityConfigService = authorityConfigService;
        this.userService = userService;
    }

    /**
     * Lists all companies with pagination.
     *
     * @param pageable the pagination parameters
     * @return a page of company responses
     */
    @GetMapping("/companies")
    public ResponseEntity<Page<CompanyResponse>> listCompanies(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Company> companies = companyService.listAll(pageable);
        Page<CompanyResponse> response = companies.map(this::toCompanyResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new company.
     *
     * @param request the company creation request
     * @return the created company response with HTTP 201 status
     */
    @PostMapping("/companies")
    public ResponseEntity<CompanyResponse> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        Company company = Company.builder()
                .nameAr(request.nameAr())
                .nameEn(request.nameEn())
                .vatNumber(request.vatNumber())
                .crNumber(request.crNumber())
                .build();
        Company saved = companyService.create(company);
        return ResponseEntity.created(URI.create("/api/admin/companies/" + saved.getId()))
                .body(toCompanyResponse(saved));
    }

    /**
     * Activates a deactivated company.
     *
     * @param id the company identifier
     * @return the activated company response
     */
    @PostMapping("/companies/{id}/activate")
    public ResponseEntity<CompanyResponse> activateCompany(@PathVariable Long id) {
        Company company = companyService.activate(id);
        return ResponseEntity.ok(toCompanyResponse(company));
    }

    /**
     * Deactivates an active company.
     *
     * @param id the company identifier
     * @return the deactivated company response
     */
    @PostMapping("/companies/{id}/deactivate")
    public ResponseEntity<CompanyResponse> deactivateCompany(@PathVariable Long id) {
        Company company = companyService.deactivate(id);
        return ResponseEntity.ok(toCompanyResponse(company));
    }

    /**
     * Updates a company's details.
     *
     * @param id the company identifier
     * @param updates a map of field names to new values
     * @return the updated company response
     */
    @PutMapping("/companies/{id}")
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable Long id, @RequestBody java.util.Map<String, String> updates) {
        Company company = companyService.update(id,
                updates.get("nameAr"), updates.get("nameEn"),
                updates.get("vatNumber"), updates.get("crNumber"));
        return ResponseEntity.ok(toCompanyResponse(company));
    }

    /**
     * Creates a branch under a company.
     *
     * @param id the company identifier
     * @param request the branch creation request
     * @return the created branch response with HTTP 201 status
     */
    @PostMapping("/companies/{id}/branches")
    public ResponseEntity<BranchResponse> createBranch(
            @PathVariable Long id, @Valid @RequestBody CreateBranchRequest request) {
        Branch branch = branchService.create(id, request.nameAr(), request.nameEn(),
                request.branchCode(), request.street(), request.buildingNumber(),
                request.additionalNumber(), request.city(), request.district(),
                request.postalCode(), request.countryCode(), request.additionalStreet());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toBranchResponse(branch));
    }

    /**
     * Lists all branches for a company.
     *
     * @param id the company identifier
     * @return a list of branch responses
     */
    @GetMapping("/companies/{id}/branches")
    public ResponseEntity<List<BranchResponse>> listBranches(@PathVariable Long id) {
        List<Branch> branches = branchService.listByCompany(id);
        List<BranchResponse> response = branches.stream()
                .map(this::toBranchResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Updates a branch under a company.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @param request the branch update request
     * @return the updated branch response
     */
    @PutMapping("/companies/{companyId}/branches/{branchId}")
    public ResponseEntity<BranchResponse> updateBranch(
            @PathVariable Long companyId, @PathVariable Long branchId,
            @Valid @RequestBody CreateBranchRequest request) {
        validateBranchBelongsToCompany(branchId, companyId);
        Branch branch = branchService.update(branchId, request.nameAr(), request.nameEn(),
                request.branchCode(), request.street(), request.buildingNumber(),
                request.additionalNumber(), request.city(), request.district(),
                request.postalCode(), request.countryCode(), request.additionalStreet());
        return ResponseEntity.ok(toBranchResponse(branch));
    }

    /**
     * Deactivates a branch (soft delete).
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @return empty response with HTTP 204
     */
    @DeleteMapping("/companies/{companyId}/branches/{branchId}")
    public ResponseEntity<Void> deactivateBranch(
            @PathVariable Long companyId, @PathVariable Long branchId) {
        validateBranchBelongsToCompany(branchId, companyId);
        branchService.deactivate(branchId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates an authority config for a branch.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @param request the authority config creation request
     * @return the created authority config response with HTTP 201 status
     */
    @PostMapping(
            "/companies/{companyId}/branches/{branchId}/authority-configs")
    public ResponseEntity<AuthorityConfigResponse> createAuthorityConfig(
            @PathVariable Long companyId, @PathVariable Long branchId,
            @Valid @RequestBody CreateAuthorityConfigRequest request) {
        validateBranchBelongsToCompany(branchId, companyId);
        AuthorityConfig config = authorityConfigService.create(
                branchId, request.authority(), request.environment(),
                request.invoicePrefix(), request.invoiceStartingNumber(),
                request.invoiceResetPolicy());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toAuthorityConfigResponse(config));
    }

    /**
     * Lists authority configs for a branch.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @return a list of authority config responses
     */
    @GetMapping(
            "/companies/{companyId}/branches/{branchId}/authority-configs")
    public ResponseEntity<List<AuthorityConfigResponse>> listAuthorityConfigs(
            @PathVariable Long companyId, @PathVariable Long branchId) {
        validateBranchBelongsToCompany(branchId, companyId);
        List<AuthorityConfig> configs = authorityConfigService.listByBranch(branchId);
        List<AuthorityConfigResponse> response = configs.stream()
                .map(this::toAuthorityConfigResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Uploads credentials for an authority config. Plaintext is encrypted internally.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @param configId the authority config identifier
     * @param credentials the raw credentials bytes to encrypt and store
     * @return the updated authority config response
     */
    @PostMapping(
            "/companies/{companyId}/branches/{branchId}"
                    + "/authority-configs/{configId}/credentials")
    public ResponseEntity<AuthorityConfigResponse> updateCredentials(
            @PathVariable Long companyId, @PathVariable Long branchId,
            @PathVariable Long configId, @RequestBody byte[] credentials) {
        validateBranchBelongsToCompany(branchId, companyId);
        AuthorityConfig config = authorityConfigService.updateEncryptedFields(
                configId, credentials, null, null, null, null);
        return ResponseEntity.ok(toAuthorityConfigResponse(config));
    }

    /**
     * Uploads a certificate for an authority config. Plaintext is encrypted internally.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @param configId the authority config identifier
     * @param certificate the raw certificate bytes to encrypt and store
     * @return the updated authority config response
     */
    @PostMapping(
            "/companies/{companyId}/branches/{branchId}"
                    + "/authority-configs/{configId}/certificate")
    public ResponseEntity<AuthorityConfigResponse> updateCertificate(
            @PathVariable Long companyId, @PathVariable Long branchId,
            @PathVariable Long configId, @RequestBody byte[] certificate) {
        validateBranchBelongsToCompany(branchId, companyId);
        AuthorityConfig config = authorityConfigService.updateEncryptedFields(
                configId, null, certificate, null, null, null);
        return ResponseEntity.ok(toAuthorityConfigResponse(config));
    }

    /**
     * Updates invoice sequence settings for an authority config.
     *
     * @param companyId the company identifier
     * @param branchId the branch identifier
     * @param configId the authority config identifier
     * @param request the authority config settings update request
     * @return the updated authority config response
     */
    @PostMapping(
            "/companies/{companyId}/branches/{branchId}"
                    + "/authority-configs/{configId}/settings")
    public ResponseEntity<AuthorityConfigResponse> updateAuthorityConfigSettings(
            @PathVariable Long companyId, @PathVariable Long branchId,
            @PathVariable Long configId,
            @Valid @RequestBody UpdateAuthorityConfigRequest request) {
        validateBranchBelongsToCompany(branchId, companyId);
        AuthorityConfig config = authorityConfigService.updateInvoiceSettings(
                configId, request.invoicePrefix(), request.invoiceStartingNumber(),
                request.invoiceResetPolicy());
        return ResponseEntity.ok(toAuthorityConfigResponse(config));
    }

    /**
     * Removes a user's role assignment from a company.
     *
     * @param companyId the company identifier
     * @param userId the user identifier
     * @return an empty response with HTTP 204 status
     */
    @DeleteMapping("/companies/{companyId}/users/{userId}")
    public ResponseEntity<Void> removeUserFromCompany(
            @PathVariable Long companyId, @PathVariable Long userId) {
        userService.removeFromCompany(userId, companyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Validates that a branch belongs to the specified company.
     *
     * @param branchId the branch identifier to check
     * @param companyId the expected company identifier
     * @throws ResponseStatusException if the branch does not belong to the company
     */
    private void validateBranchBelongsToCompany(Long branchId, Long companyId) {
        Branch branch = branchService.getById(branchId);
        if (!branch.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Branch " + branchId + " does not belong to company " + companyId);
        }
    }

    private CompanyResponse toCompanyResponse(Company c) {
        return new CompanyResponse(c.getId(), c.getNameAr(), c.getNameEn(),
                c.getVatNumber(), c.getCrNumber(),
                c.getIsActive(), c.getCreatedAt());
    }

    private BranchResponse toBranchResponse(Branch b) {
        return new BranchResponse(b.getId(), b.getCompany().getId(),
                b.getNameAr(), b.getNameEn(), b.getBranchCode(),
                b.getStreet(), b.getBuildingNumber(), b.getAdditionalNumber(),
                b.getCity(), b.getDistrict(), b.getPostalCode(),
                b.getCountryCode(), b.getAdditionalStreet(),
                b.getIsActive(), b.getCreatedAt());
    }

    private AuthorityConfigResponse toAuthorityConfigResponse(AuthorityConfig c) {
        return new AuthorityConfigResponse(c.getId(), c.getBranch().getId(),
                c.getAuthority(), c.getEnvironment(),
                c.getCredentialsEncrypted() != null,
                c.getCertificateEncrypted() != null,
                c.getCsidEncrypted() != null,
                c.getPrivateKeyEncrypted() != null,
                c.getTokenDataEncrypted() != null,
                c.getCertificateExpiryDate() != null
                        ? c.getCertificateExpiryDate().toString() : null,
                c.getInvoiceCounter(), c.getInvoicePrefix(),
                c.getInvoiceStartingNumber(), c.getInvoiceResetPolicy(),
                c.getEnabledDocumentTypes(), c.getIsActive());
    }
}
