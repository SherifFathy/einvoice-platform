package com.einvoice.security.permission;

import com.einvoice.core.domain.rbac.TransactionRole;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import com.einvoice.core.repository.rbac.TransactionRolePermissionRepository;
import com.einvoice.core.repository.rbac.TransactionRoleRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Javadoc. */
@Service
public class PermissionService {

    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final TransactionRoleRepository transactionRoleRepository;
    private final TransactionRolePermissionRepository trpRepository;
    private final AuthorityEnvironmentRepository authEnvRepository;
    private final PermissionCache permissionCache;

    /**
     * Javadoc.
     * @param uctrRepository user-company-transaction-role repository
     * @param transactionRoleRepository transaction role repository
     * @param trpRepository transaction role permission repository
     * @param authEnvRepository authority environment repository
     * @param permissionCache request-scoped permission cache
     */
    public PermissionService(
            UserCompanyTransactionRoleRepository uctrRepository,
            TransactionRoleRepository transactionRoleRepository,
            TransactionRolePermissionRepository trpRepository,
            AuthorityEnvironmentRepository authEnvRepository,
            PermissionCache permissionCache) {
        this.uctrRepository = uctrRepository;
        this.transactionRoleRepository = transactionRoleRepository;
        this.trpRepository = trpRepository;
        this.authEnvRepository = authEnvRepository;
        this.permissionCache = permissionCache;
    }

    public Set<String> permissionsFor(UUID userId, UUID companyId, short authEnvId,
            String transactionType) {
        return permissionCache.getOrCompute(userId, companyId, authEnvId, transactionType,
                () -> loadPermissions(userId, companyId, authEnvId, transactionType));
    }

    public boolean hasPermission(UUID userId, UUID companyId, short authEnvId,
            String transactionType, String permissionCode) {
        return permissionsFor(userId, companyId, authEnvId, transactionType)
                .contains(permissionCode);
    }

    private Set<String> loadPermissions(UUID userId, UUID companyId, short authEnvId,
            String transactionType) {
        String authority = resolveAuthority(authEnvId);

        if (transactionType != null) {
            return loadPermissionsForTxType(userId, companyId, authEnvId,
                    transactionType, authority);
        }

        return loadAllPermissions(userId, companyId, authEnvId, authority);
    }

    private Set<String> loadPermissionsForTxType(UUID userId, UUID companyId,
            short authEnvId, String transactionType, String authority) {
        List<UserCompanyTransactionRole> assignments = uctrRepository
                .findByUserIdAndCompanyIdAndAuthorityEnvironmentIdAndIsActiveTrue(
                        userId, companyId, authEnvId);

        Set<String> allPermissions = new HashSet<>();
        for (UserCompanyTransactionRole assignment : assignments) {
            if (!assignment.getTransactionType().equals(transactionType)) {
                continue;
            }
            transactionRoleRepository
                    .findByAuthorityAndTransactionTypeAndRoleCode(
                            authority, transactionType, assignment.getRoleCode())
                    .ifPresent(role -> {
                        Set<String> perms = trpRepository.findPermissionCodesByRoleId(role.getId());
                        allPermissions.addAll(perms);
                    });
        }
        return allPermissions;
    }

    private Set<String> loadAllPermissions(UUID userId, UUID companyId,
            short authEnvId, String authority) {
        List<UserCompanyTransactionRole> assignments = uctrRepository
                .findByUserIdAndCompanyIdAndAuthorityEnvironmentIdAndIsActiveTrue(
                        userId, companyId, authEnvId);

        if (assignments.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> allPermissions = new HashSet<>();
        for (UserCompanyTransactionRole assignment : assignments) {
            transactionRoleRepository
                    .findByAuthorityAndTransactionTypeAndRoleCode(
                            authority,
                            assignment.getTransactionType(),
                            assignment.getRoleCode())
                    .ifPresent(role -> {
                        Set<String> perms = trpRepository.findPermissionCodesByRoleId(role.getId());
                        allPermissions.addAll(perms);
                    });
        }
        return allPermissions;
    }

    private String resolveAuthority(short authEnvId) {
        return authEnvRepository.findById(authEnvId)
                .map(ae -> ae.getAuthority())
                .orElseThrow(() -> new IllegalStateException(
                        "No AuthorityEnvironment found for id " + authEnvId));
    }
}
