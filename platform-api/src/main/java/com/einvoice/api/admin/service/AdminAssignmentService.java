package com.einvoice.api.admin.service;

import com.einvoice.api.admin.dto.AssignmentCreateRequest;
import com.einvoice.api.admin.dto.AssignmentResponse;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.AuthorityEnvironment;
import com.einvoice.core.domain.rbac.TransactionRole;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.error.AssignmentExistsException;
import com.einvoice.core.error.InvalidRoleForAuthorityException;
import com.einvoice.core.error.TaxNumberDuplicateException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import com.einvoice.core.repository.rbac.TransactionRoleRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for user-company assignment administration operations. */
@Service
@Transactional
public class AdminAssignmentService {

    private final UserCompanyTransactionRoleRepository uctrRepo;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final TransactionRoleRepository transactionRoleRepository;
    private final AuthorityEnvironmentRepository authEnvRepo;

    /**
     * Constructs an AdminAssignmentService with the required repositories.
     *
     * @param uctrRepo the UCTR repository
     * @param userRepository the user repository
     * @param companyRepository the company repository
     * @param transactionRoleRepository the transaction role repository
     * @param authEnvRepo the authority environment repository
     */
    public AdminAssignmentService(UserCompanyTransactionRoleRepository uctrRepo,
            UserRepository userRepository,
            CompanyRepository companyRepository,
            TransactionRoleRepository transactionRoleRepository,
            AuthorityEnvironmentRepository authEnvRepo) {
        this.uctrRepo = uctrRepo;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.transactionRoleRepository = transactionRoleRepository;
        this.authEnvRepo = authEnvRepo;
    }

    /**
     * Creates a new user-company transaction role assignment.
     *
     * @param userId the user ID
     * @param request the assignment request
     * @return the created assignment response
     */
    public AssignmentResponse create(UUID userId, AssignmentCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        AuthorityEnvironment authEnv = authEnvRepo.findById(request.authorityEnvironmentId())
                .orElseThrow(() -> new IllegalArgumentException("Authority environment not found"));

        TransactionRole role = transactionRoleRepository
                .findByAuthorityAndTransactionTypeAndRoleCode(
                        authEnv.getAuthority(), request.transactionType(), request.roleCode())
                .orElseThrow(() -> new InvalidRoleForAuthorityException(
                        "No role found for authority=" + authEnv.getAuthority()
                                + ", transactionType=" + request.transactionType()
                                + ", roleCode=" + request.roleCode()));

        checkTaxNumberUniqueness(company, authEnv.getId(), request.companyId());

        UserCompanyTransactionRole assignment = UserCompanyTransactionRole.builder()
                .user(user)
                .company(company)
                .authorityEnvironmentId(authEnv.getId())
                .transactionType(request.transactionType())
                .roleCode(request.roleCode())
                .isActive(true)
                .grantedAt(java.time.OffsetDateTime.now())
                .build();

        try {
            UserCompanyTransactionRole saved = uctrRepo.saveAndFlush(assignment);
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new AssignmentExistsException(
                    "Assignment already exists for this user, company, authority environment, and transaction type");
        }
    }

    /**
     * Soft-deletes an assignment by setting it inactive.
     *
     * @param userId the user ID
     * @param assignmentId the assignment ID
     */
    public void delete(UUID userId, UUID assignmentId) {
        UserCompanyTransactionRole assignment = uctrRepo.findById(assignmentId)
                .filter(a -> a.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        assignment.setIsActive(false);
        uctrRepo.save(assignment);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listForUser(UUID userId) {
        return uctrRepo.findByUserIdAndIsActiveTrue(userId).stream()
                .map(this::toResponse).toList();
    }

    private void checkTaxNumberUniqueness(Company company, Short authEnvId, UUID companyIdToExclude) {
        if (company.getTaxNumber() == null) {
            return;
        }

        List<UUID> companyIdsWithAssignments = uctrRepo
                .findDistinctCompanyIdsByAuthorityEnvironmentIdAndIsActiveTrue(authEnvId);

        List<Company> activeCompanies = companyRepository.findByIsActiveTrue();

        boolean duplicate = activeCompanies.stream()
                .filter(c -> !c.getId().equals(companyIdToExclude))
                .filter(c -> companyIdsWithAssignments.contains(c.getId()))
                .anyMatch(c -> company.getTaxNumber().equals(c.getTaxNumber()));

        if (duplicate) {
            throw new TaxNumberDuplicateException(
                    "Another active company in this authority environment already uses tax number '"
                            + company.getTaxNumber() + "'");
        }
    }

    private AssignmentResponse toResponse(UserCompanyTransactionRole a) {
        return new AssignmentResponse(
                a.getId(), a.getUser().getId(), a.getCompany().getId(),
                a.getAuthorityEnvironmentId(), a.getTransactionType(), a.getRoleCode(),
                a.getIsActive(),
                a.getGrantedBy() != null ? a.getGrantedBy().getId() : null,
                a.getGrantedAt());
    }
}
