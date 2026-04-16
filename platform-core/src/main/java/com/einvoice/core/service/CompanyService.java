package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.Company;
import com.einvoice.core.repository.CompanyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing companies. */
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;

    /**
     * Creates the company service.
     *
     * @param companyRepository the company repository
     */
    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Creates a new company.
     *
     * @param company the company to create
     * @return the saved company
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "company.create", entityType = "Company")
    public Company create(Company company) {
        if (companyRepository.existsByVatNumber(company.getVatNumber())) {
            throw new DuplicateVatNumberException(
                    "Company with VAT number " + company.getVatNumber() + " already exists");
        }
        return companyRepository.save(company);
    }

    /**
     * Activates a company.
     *
     * @param id the company identifier
     * @return the activated company
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "company.activate", entityType = "Company", entityClass = Company.class)
    public Company activate(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + id));
        company.setIsActive(true);
        return company;
    }

    /**
     * Deactivates a company.
     *
     * @param id the company identifier
     * @return the deactivated company
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "company.deactivate", entityType = "Company", entityClass = Company.class)
    public Company deactivate(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + id));
        company.setIsActive(false);
        return company;
    }

    /**
     * Lists all companies with pagination.
     *
     * @param pageable pagination information
     * @return a page of companies
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public Page<Company> listAll(Pageable pageable) {
        return companyRepository.findAll(pageable);
    }

    /**
     * Updates a company's profile fields.
     *
     * @param id the company identifier
     * @param nameAr the Arabic name
     * @param nameEn the English name
     * @param vatNumber the VAT number
     * @param crNumber the commercial registration number
     * @param street the street address
     * @param buildingNumber the building number
     * @param city the city
     * @param district the district
     * @param postalCode the postal code
     * @param countryCode the country code
     * @param additionalId the additional identifier
     * @return the updated company
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "company.update", entityType = "Company", entityClass = Company.class)
    public Company update(Long id, String nameAr, String nameEn, String vatNumber,
            String crNumber, String street, String buildingNumber, String city,
            String district, String postalCode, String countryCode, String additionalId) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + id));
        if (nameAr != null) {
            company.setNameAr(nameAr);
        }
        if (nameEn != null) {
            company.setNameEn(nameEn);
        }
        if (vatNumber != null) {
            company.setVatNumber(vatNumber);
        }
        if (crNumber != null) {
            company.setCrNumber(crNumber);
        }
        if (street != null) {
            company.setStreet(street);
        }
        if (buildingNumber != null) {
            company.setBuildingNumber(buildingNumber);
        }
        if (city != null) {
            company.setCity(city);
        }
        if (district != null) {
            company.setDistrict(district);
        }
        if (postalCode != null) {
            company.setPostalCode(postalCode);
        }
        if (countryCode != null) {
            company.setCountryCode(countryCode);
        }
        if (additionalId != null) {
            company.setAdditionalId(additionalId);
        }
        return company;
    }

    /**
     * Retrieves a company by its identifier.
     *
     * @param id the company identifier
     * @return the company
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public Company getById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + id));
    }

    /** Exception thrown when a company is not found. */
    public static class CompanyNotFoundException extends RuntimeException {
        /**
         * Creates a new CompanyNotFoundException.
         *
         * @param message the detail message
         */
        public CompanyNotFoundException(String message) {
            super(message);
        }
    }

    /** Exception thrown when a duplicate VAT number is detected. */
    public static class DuplicateVatNumberException extends RuntimeException {
        /**
         * Creates a new DuplicateVatNumberException.
         *
         * @param message the detail message
         */
        public DuplicateVatNumberException(String message) {
            super(message);
        }
    }
}
