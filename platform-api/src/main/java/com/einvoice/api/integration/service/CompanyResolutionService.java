package com.einvoice.api.integration.service;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.AuthorityEnvironment;
import com.einvoice.core.error.AuthorityEnvironmentNotFoundException;
import com.einvoice.core.error.CompanyNotFoundException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves (companyRegistrationNumber, authority, environment) to (companyId, authorityEnvironmentId). */
@Service
public class CompanyResolutionService {

    private final CompanyRepository companyRepository;
    private final AuthorityEnvironmentRepository authorityEnvironmentRepository;

    /**
     * Constructs a new CompanyResolutionService.
     *
     * @param companyRepository the company repository
     * @param authorityEnvironmentRepository the authority environment repository
     */
    public CompanyResolutionService(CompanyRepository companyRepository,
            AuthorityEnvironmentRepository authorityEnvironmentRepository) {
        this.companyRepository = companyRepository;
        this.authorityEnvironmentRepository = authorityEnvironmentRepository;
    }

    /** Resolved tenant context carrying companyId and authorityEnvironmentId. */
    public record ResolvedContext(UUID companyId, Short authorityEnvironmentId) {}

    /** Resolves the company and authority-environment from the request parameters.
     * @param companyRegistrationNumber the tax number to look up
     * @param authority the authority name (e.g. ETA, ZATCA)
     * @param environment the environment name (e.g. SANDBOX, PREPROD)
     * @return the resolved context
     */
    @Transactional(readOnly = true)
    public ResolvedContext resolve(String companyRegistrationNumber, String authority, String environment) {
        Company company = companyRepository.findByTaxNumberAndIsActiveTrue(companyRegistrationNumber)
                .orElseThrow(() -> new CompanyNotFoundException(companyRegistrationNumber));

        AuthorityEnvironment ae = authorityEnvironmentRepository
                .findByAuthorityAndEnvironmentAndIsActiveTrue(authority, environment)
                .orElseThrow(() -> new AuthorityEnvironmentNotFoundException(authority, environment));

        return new ResolvedContext(company.getId(), ae.getId());
    }
}
