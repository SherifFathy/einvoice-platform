package com.einvoice.api.company;

import com.einvoice.api.company.dto.CompanyProfileResponse;
import com.einvoice.api.company.dto.UpdateCompanyRequest;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for Company Admin company profile management. */
@RestController
@RequestMapping("/api/companies")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class CompanyController {

    private final CompanyService companyService;

    /**
     * Creates the controller.
     *
     * @param companyService the company service
     */
    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Gets the company profile.
     *
     * @param id the company identifier
     * @return the company profile
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<CompanyProfileResponse> getCompany(@PathVariable Long id) {
        validateTenantAccess(id);
        Company company = companyService.getById(id);
        return ResponseEntity.ok(toResponse(company));
    }

    /**
     * Updates the company profile.
     *
     * @param id the company identifier
     * @param request the update request
     * @return the updated company profile
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CompanyProfileResponse> updateCompany(
            @PathVariable Long id, @Valid @RequestBody UpdateCompanyRequest request) {
        validateTenantAccess(id);
        Company company = companyService.update(id, request.nameAr(), request.nameEn(),
                request.vatNumber(), request.crNumber(), request.street(),
                request.buildingNumber(), request.city(), request.district(),
                request.postalCode(), request.countryCode(), request.additionalId());
        return ResponseEntity.ok(toResponse(company));
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

    private CompanyProfileResponse toResponse(Company c) {
        return new CompanyProfileResponse(c.getId(), c.getNameAr(), c.getNameEn(),
                c.getVatNumber(), c.getCrNumber(), c.getStreet(),
                c.getBuildingNumber(), c.getCity(), c.getDistrict(),
                c.getPostalCode(), c.getCountryCode(), c.getAdditionalId(),
                c.getIsActive(), c.getCreatedAt());
    }
}
