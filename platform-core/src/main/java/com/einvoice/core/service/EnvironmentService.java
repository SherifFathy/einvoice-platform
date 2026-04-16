package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.UserEnvironmentPermission;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.exception.NoRoleInCompanyException;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserEnvironmentPermissionRepository;
import com.einvoice.core.repository.UserRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for querying environment permissions. */
@Service
public class EnvironmentService {

    private final UserEnvironmentPermissionRepository userEnvironmentPermissionRepository;
    private final UserCompanyRoleRepository userCompanyRoleRepository;
    private final UserRepository userRepository;

    /**
     * Creates an EnvironmentService with the required dependencies.
     *
     * @param userEnvironmentPermissionRepository the environment permission repository
     * @param userCompanyRoleRepository the user-company role repository
     * @param userRepository the user repository
     */
    public EnvironmentService(
            UserEnvironmentPermissionRepository userEnvironmentPermissionRepository,
            UserCompanyRoleRepository userCompanyRoleRepository,
            UserRepository userRepository) {
        this.userEnvironmentPermissionRepository = userEnvironmentPermissionRepository;
        this.userCompanyRoleRepository = userCompanyRoleRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns the list of environment names the user has permission for in the given company.
     *
     * @param userId the user's ID
     * @param companyId the company ID
     * @return list of permitted environment names
     * @throws NoRoleInCompanyException if the user has no role in the company
     */
    @Transactional(readOnly = true)
    public List<String> getPermittedEnvironments(Long userId, Long companyId) {
        UserCompanyRole role = userCompanyRoleRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new NoRoleInCompanyException(
                        "User has no role in company " + companyId));

        return userEnvironmentPermissionRepository
                .findByUserCompanyRoleId(role.getId()).stream()
                .map(uep -> uep.getEnvironment().name())
                .toList();
    }

    /**
     * Checks whether a user has permission for a specific environment in a company.
     *
     * @param userId the user's ID
     * @param companyId the company ID
     * @param environmentName the environment name to check
     * @return true if the user has permission, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasEnvironmentPermission(Long userId, Long companyId, String environmentName) {
        Environment environment;
        try {
            environment = Environment.valueOf(environmentName);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return userCompanyRoleRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .map(role -> userEnvironmentPermissionRepository
                        .existsByUserCompanyRoleIdAndEnvironment(role.getId(), environment))
                .orElse(false);
    }

    /**
     * Assigns environment permissions for a user in a company.
     * Replaces all existing permissions with the provided list.
     *
     * @param userId the user identifier
     * @param companyId the company identifier
     * @param environments the list of environments to grant
     * @param grantedByUserId the user who granted the permissions
     * @return the list of created permissions
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "user.assign_permissions", entityType = "UserEnvironmentPermission")
    public List<UserEnvironmentPermission> assignPermissions(Long userId, Long companyId,
            List<Environment> environments, Long grantedByUserId) {
        UserCompanyRole role = userCompanyRoleRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new NoRoleInCompanyException(
                        "User has no role in company " + companyId));

        userEnvironmentPermissionRepository
                .findByUserCompanyRoleId(role.getId())
                .forEach(userEnvironmentPermissionRepository::delete);

        User grantedBy = grantedByUserId != null
                ? userRepository.findById(grantedByUserId).orElse(null) : null;

        List<UserEnvironmentPermission> permissions = environments.stream()
                .map(env -> UserEnvironmentPermission.builder()
                        .userCompanyRole(role)
                        .environment(env)
                        .grantedBy(grantedBy)
                        .build())
                .toList();
        return userEnvironmentPermissionRepository.saveAll(permissions);
    }
}
