package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.repository.BranchRepository;
import com.einvoice.core.repository.CompanyRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing company branches. */
@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;

    /**
     * Creates the branch service.
     *
     * @param branchRepository the branch repository
     * @param companyRepository the company repository
     */
    public BranchService(BranchRepository branchRepository,
            CompanyRepository companyRepository) {
        this.branchRepository = branchRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Creates a new branch under a company.
     *
     * @param companyId the company identifier
     * @param nameAr the Arabic name
     * @param nameEn the English name
     * @param branchCode the branch code
     * @return the created branch
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "branch.create", entityType = "Branch")
    public Branch create(Long companyId, String nameAr, String nameEn, String branchCode) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));
        Branch branch = Branch.builder()
                .company(company)
                .nameAr(nameAr)
                .nameEn(nameEn)
                .branchCode(branchCode)
                .isActive(true)
                .build();
        return branchRepository.save(branch);
    }

    /**
     * Updates an existing branch.
     *
     * @param branchId the branch identifier
     * @param nameAr the Arabic name
     * @param nameEn the English name
     * @param branchCode the branch code
     * @return the updated branch
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "branch.update", entityType = "Branch", entityClass = Branch.class)
    public Branch update(Long branchId, String nameAr, String nameEn, String branchCode) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BranchNotFoundException("Branch not found: " + branchId));
        branch.setNameAr(nameAr);
        branch.setNameEn(nameEn);
        branch.setBranchCode(branchCode);
        return branch;
    }

    /**
     * Deactivates a branch (soft delete).
     *
     * @param branchId the branch identifier
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "branch.deactivate", entityType = "Branch", entityClass = Branch.class)
    public void deactivate(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BranchNotFoundException("Branch not found: " + branchId));
        branch.setIsActive(false);
    }

    /**
     * Lists all branches for a company.
     *
     * @param companyId the company identifier
     * @return list of branches ordered by creation time
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public List<Branch> listByCompany(Long companyId) {
        return branchRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
    }

    /**
     * Lists active branches for a company.
     *
     * @param companyId the company identifier
     * @return list of active branches ordered by creation time
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public List<Branch> listActiveByCompany(Long companyId) {
        return branchRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtAsc(companyId);
    }

    /**
     * Retrieves a branch by its identifier.
     *
     * @param id the branch identifier
     * @return the branch
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public Branch getById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Branch not found: " + id));
    }

    /** Exception thrown when a branch is not found. */
    public static class BranchNotFoundException extends RuntimeException {
        /**
         * Creates a new BranchNotFoundException.
         *
         * @param message the detail message
         */
        public BranchNotFoundException(String message) {
            super(message);
        }
    }
}
