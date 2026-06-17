package com.einvoice.api.branch;

import com.einvoice.api.branch.dto.BranchLookupResponse;
import com.einvoice.core.domain.branch.Branch;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.repository.branch.BranchRepository;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.security.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides active branches visible to the current session. */
@Service
@Transactional(readOnly = true)
public class BranchLookupService {

    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;
    private final UserCompanyTransactionRoleRepository uctrRepository;

    /**
     * Constructs the lookup service.
     *
     * @param branchRepository branch repository
     * @param companyRepository company repository
     * @param uctrRepository user-company role repository
     */
    public BranchLookupService(BranchRepository branchRepository,
            CompanyRepository companyRepository,
            UserCompanyTransactionRoleRepository uctrRepository) {
        this.branchRepository = branchRepository;
        this.companyRepository = companyRepository;
        this.uctrRepository = uctrRepository;
    }

    /**
     * Lists active branches for the companies visible to the active session.
     *
     * @return visible branch options
     */
    public List<BranchLookupResponse> listVisibleBranches() {
        List<UUID> companyIds = visibleCompanyIds();
        if (companyIds.isEmpty()) {
            return List.of();
        }
        return branchRepository.findVisibleActiveBranches(companyIds).stream()
                .map(this::toResponse)
                .toList();
    }

    private List<UUID> visibleCompanyIds() {
        if (TenantContext.isSuperUser()) {
            return companyRepository.findByIsActiveTrue().stream()
                    .map(Company::getId)
                    .toList();
        }
        return uctrRepository.findDistinctAssignedCompanyIds(
                TenantContext.getUserId(),
                TenantContext.getAuthorityEnvironmentId());
    }

    private BranchLookupResponse toResponse(Branch branch) {
        Company company = branch.getCompany();
        return new BranchLookupResponse(
                branch.getId(),
                company.getId(),
                company.getNameEn(),
                branch.getNameEn(),
                branch.getNameAr(),
                branch.getBranchCode(),
                branch.getIsActive());
    }
}
