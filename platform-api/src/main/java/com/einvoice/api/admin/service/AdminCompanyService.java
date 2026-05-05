package com.einvoice.api.admin.service;

import com.einvoice.api.admin.dto.CompanyCreateRequest;
import com.einvoice.api.admin.dto.CompanyResponse;
import com.einvoice.api.admin.dto.CompanyUpdateRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.repository.company.CompanyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for company administration operations. */
@Service
@Transactional
public class AdminCompanyService {

    private final CompanyRepository companyRepository;

    public AdminCompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Creates a new company.
     *
     * @param request the company creation request
     * @return the created company response
     */
    public CompanyResponse create(CompanyCreateRequest request) {
        Company company = Company.builder()
                .nameEn(request.nameEn())
                .nameAr(request.nameAr())
                .taxNumber(request.taxNumber())
                .crNumber(request.crNumber())
                .isActive(true)
                .build();
        Company saved = companyRepository.save(company);
        return toResponse(saved);
    }

    /**
     * Updates an existing company.
     *
     * @param id the company ID
     * @param request the company update request
     * @return the updated company response
     */
    public CompanyResponse update(UUID id, CompanyUpdateRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        company.setNameEn(request.nameEn());
        company.setNameAr(request.nameAr());
        company.setTaxNumber(request.taxNumber());
        company.setCrNumber(request.crNumber());
        Company saved = companyRepository.save(company);
        return toResponse(saved);
    }

    /**
     * Deactivates a company by setting isActive to false.
     *
     * @param id the company ID
     */
    public void deactivate(UUID id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        company.setIsActive(false);
        companyRepository.save(company);
    }

    /**
     * Lists companies, optionally including inactive ones.
     *
     * @param includeInactive whether to include inactive companies
     * @return the list of company responses
     */
    @Transactional(readOnly = true)
    public List<CompanyResponse> list(boolean includeInactive) {
        List<Company> companies = includeInactive
                ? companyRepository.findAll()
                : companyRepository.findByIsActiveTrue();
        return companies.stream().map(this::toResponse).toList();
    }

    private CompanyResponse toResponse(Company c) {
        return new CompanyResponse(
                c.getId(), c.getNameEn(), c.getNameAr(), c.getTaxNumber(),
                c.getCrNumber(), c.getLogoPath(), c.getIsActive(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
