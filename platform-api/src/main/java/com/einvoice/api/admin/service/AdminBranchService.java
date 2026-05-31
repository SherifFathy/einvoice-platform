package com.einvoice.api.admin.service;

import com.einvoice.api.admin.dto.BranchCreateRequest;
import com.einvoice.api.admin.dto.BranchResponse;
import com.einvoice.api.admin.dto.BranchUpdateRequest;
import com.einvoice.core.domain.branch.Branch;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.error.BranchCodeDuplicateException;
import com.einvoice.core.repository.branch.BranchRepository;
import com.einvoice.core.repository.company.CompanyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for branch administration operations. */
@Service
@Transactional
public class AdminBranchService {

    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;

    public AdminBranchService(BranchRepository branchRepository, CompanyRepository companyRepository) {
        this.branchRepository = branchRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Creates a new branch under the specified company.
     *
     * @param companyId the company ID
     * @param request the branch creation request
     * @return the created branch response
     */
    public BranchResponse create(UUID companyId, BranchCreateRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        if (request.branchCode() != null && !request.branchCode().isBlank()) {
            branchRepository.findByCompanyIdAndBranchCode(companyId, request.branchCode())
                    .ifPresent(b -> {
                        throw new BranchCodeDuplicateException(
                                "Branch code '" + request.branchCode() + "' already exists in this company");
                    });
        }

        Branch branch = Branch.builder()
                .company(company)
                .nameEn(request.nameEn())
                .nameAr(request.nameAr())
                .branchCode(request.branchCode())
                .addressLine1(request.addressLine1())
                .addressLine2(request.addressLine2())
                .city(request.city())
                .region(request.region())
                .postalCode(request.postalCode())
                .country(request.country() != null ? request.country() : "EG")
                .buildingNumber(request.buildingNumber())
                .additionalNo(request.additionalNo())
                .taxpayerActivityCode(request.taxpayerActivityCode())
                .isActive(true)
                .build();
        Branch saved = branchRepository.save(branch);
        return toResponse(saved);
    }

    /**
     * Updates an existing branch.
     *
     * @param id the branch ID
     * @param request the branch update request
     * @return the updated branch response
     */
    public BranchResponse update(UUID id, BranchUpdateRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));

        if (request.branchCode() != null && !request.branchCode().isBlank()) {
            branchRepository.findByCompanyIdAndBranchCode(branch.getCompany().getId(), request.branchCode())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(b -> {
                        throw new BranchCodeDuplicateException(
                                "Branch code '" + request.branchCode() + "' already exists in this company");
                    });
        }

        branch.setNameEn(request.nameEn());
        branch.setNameAr(request.nameAr());
        branch.setBranchCode(request.branchCode());
        branch.setAddressLine1(request.addressLine1());
        branch.setAddressLine2(request.addressLine2());
        branch.setCity(request.city());
        branch.setRegion(request.region());
        branch.setPostalCode(request.postalCode());
        branch.setCountry(request.country() != null ? request.country() : "EG");
        branch.setBuildingNumber(request.buildingNumber());
        branch.setAdditionalNo(request.additionalNo());
        branch.setTaxpayerActivityCode(request.taxpayerActivityCode());
        Branch saved = branchRepository.save(branch);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> listForCompany(UUID companyId) {
        return branchRepository.findByCompanyIdAndIsActiveTrue(companyId).stream()
                .map(this::toResponse).toList();
    }

    private BranchResponse toResponse(Branch b) {
        return new BranchResponse(
                b.getId(), b.getCompany().getId(), b.getNameEn(), b.getNameAr(),
                b.getBranchCode(), b.getAddressLine1(), b.getAddressLine2(),
                b.getCity(), b.getRegion(), b.getPostalCode(), b.getCountry(),
                b.getBuildingNumber(), b.getAdditionalNo(), b.getTaxpayerActivityCode(),
                b.getIsActive(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
