package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.LovContext;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.UserContextPermission;
import com.einvoice.core.domain.enums.Role;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.LovContextRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserContextPermissionRepository;
import com.einvoice.core.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing users and their company role assignments. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserCompanyRoleRepository userCompanyRoleRepository;
    private final UserContextPermissionRepository userContextPermissionRepository;
    private final LovContextRepository lovContextRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    private final AuditService auditService;

    /**
     * Creates the user service.
     *
     * @param userRepository the user repository
     * @param userCompanyRoleRepository the user-company role repository
     * @param userContextPermissionRepository the user-context permission repository
     * @param lovContextRepository the LOV context repository
     * @param companyRepository the company repository
     * @param passwordEncoder the password encoder
     * @param auditService the audit service
     */
    public UserService(UserRepository userRepository,
            UserCompanyRoleRepository userCompanyRoleRepository,
            UserContextPermissionRepository userContextPermissionRepository,
            LovContextRepository lovContextRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.userCompanyRoleRepository = userCompanyRoleRepository;
        this.userContextPermissionRepository = userContextPermissionRepository;
        this.lovContextRepository = lovContextRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Retrieves an existing user by email or creates a new one.
     *
     * @param email the user email
     * @param name the user display name
     * @return the existing or newly created user
     */
    @Transactional
    @Audited(action = "user.create_or_get", entityType = "User")
    public User getOrCreateUser(String email, String name) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .name(name != null ? name : email)
                            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .isActive(true)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    /**
     * Assigns a user to a company with a specific role.
     *
     * @param userId the user identifier
     * @param companyId the company identifier
     * @param role the role to assign
     * @param grantedByUserId the user who granted the role
     * @return the created user-company role assignment
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "USER_COMPANY_ASSIGNED", entityType = "UserCompanyRole")
    public UserCompanyRole assignToCompany(Long userId, Long companyId, Role role,
            Long grantedByUserId) {
        if (userCompanyRoleRepository.existsByUserIdAndCompanyId(userId, companyId)) {
            throw new UserAlreadyAssignedException(
                    "User " + userId + " already has a role in company " + companyId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        User grantedBy = grantedByUserId != null
                ? userRepository.findById(grantedByUserId).orElse(null) : null;

        UserCompanyRole assignment = UserCompanyRole.builder()
                .user(user)
                .company(companyRepository.getReferenceById(companyId))
                .role(role)
                .grantedBy(grantedBy)
                .isActive(true)
                .build();
        return userCompanyRoleRepository.save(assignment);
    }

    /**
     * Removes a user's role assignment from a company.
     *
     * @param userId the user identifier
     * @param companyId the company identifier
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "USER_COMPANY_REMOVED",
             entityType = "UserCompanyRole",
             entityClass = UserCompanyRole.class)
    public void removeFromCompany(Long userId, Long companyId) {
        UserCompanyRole role = userCompanyRoleRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new UserNotAssignedException(
                        "User " + userId + " is not assigned to company " + companyId));
        userCompanyRoleRepository.delete(role);
    }

    /**
     * Retrieves a user by email.
     *
     * @param email the user email
     * @return the user
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    /**
     * Retrieves a user by identifier.
     *
     * @param id the user identifier
     * @return the user
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    /**
     * Activates a user's role in a company.
     *
     * @param userId the user identifier
     * @param companyId the company identifier
     * @return the updated user-company role
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "user.activate_role", entityType = "UserCompanyRole",
            entityClass = UserCompanyRole.class)
    public UserCompanyRole activateRole(Long userId, Long companyId) {
        UserCompanyRole role = userCompanyRoleRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new UserNotAssignedException(
                        "User " + userId + " is not assigned to company " + companyId));
        role.setIsActive(true);
        return role;
    }

    /**
     * Deactivates a user's role in a company.
     *
     * @param userId the user identifier
     * @param companyId the company identifier
     * @return the updated user-company role
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "user.deactivate_role", entityType = "UserCompanyRole",
            entityClass = UserCompanyRole.class)
    public UserCompanyRole deactivateRole(Long userId, Long companyId) {
        UserCompanyRole role = userCompanyRoleRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new UserNotAssignedException(
                        "User " + userId + " is not assigned to company " + companyId));
        role.setIsActive(false);
        return role;
    }

    /**
     * Lists all users. Requires SUPER_USER role.
     *
     * @return list of all users
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_USER')")
    public List<User> listAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Creates a new user with the given details.
     *
     * @param name the user display name
     * @param email the user email
     * @param password the plaintext password
     * @return the newly created user
     */
    @Transactional
    @PreAuthorize("hasRole('SUPER_USER')")
    @Audited(action = "USER_CREATED", entityType = "User")
    public User createUser(String name, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyAssignedException("Email already in use: " + email);
        }
        User user = User.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    /**
     * Updates a user's name and/or email.
     *
     * @param id the user identifier
     * @param name the new display name (may be {@code null})
     * @param email the new email (may be {@code null})
     * @return the updated user
     */
    @Transactional
    @PreAuthorize("hasRole('SUPER_USER')")
    @Audited(action = "USER_UPDATED", entityType = "User", entityClass = User.class)
    public User updateUser(Long id, String name, String email) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        if (name != null) {
            user.setName(name);
        }
        if (email != null) {
            user.setEmail(email);
        }
        return user;
    }

    /**
     * Resets a user's password.
     *
     * @param id the user identifier
     * @param newPassword the new plaintext password
     */
    @Transactional
    @PreAuthorize("hasRole('SUPER_USER')")
    @Audited(action = "USER_PASSWORD_RESET", entityType = "User", entityClass = User.class)
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    /**
     * Activates a user account.
     *
     * @param id the user identifier
     * @return the activated user
     */
    @Transactional
    @PreAuthorize("hasRole('SUPER_USER')")
    @Audited(action = "USER_ACTIVATED", entityType = "User", entityClass = User.class)
    public User activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
        user.setIsActive(true);
        return user;
    }

    /**
     * Deactivates a user, preventing login. Guards against removing the last Super User.
     *
     * @param actorId the user performing the action
     * @param targetId the user to deactivate
     * @return the deactivated user
     */
    @Transactional
    @PreAuthorize("hasRole('SUPER_USER')")
    @Audited(action = "USER_DEACTIVATED", entityType = "User", entityClass = User.class)
    public User deactivateUser(Long actorId, Long targetId) {
        guardLastSuperUser(targetId);
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + targetId));
        user.setIsActive(false);
        return user;
    }

    /**
     * Soft-deletes a user. Guards against removing the last Super User.
     *
     * @param actorId the user performing the action
     * @param targetId the user to delete
     */
    @Transactional
    @PreAuthorize("hasRole('SUPER_USER')")
    @Audited(action = "USER_DELETED", entityType = "User", entityClass = User.class)
    public void deleteUser(Long actorId, Long targetId) {
        guardLastSuperUser(targetId);
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + targetId));
        user.setIsActive(false);
        user.setDeletedAt(OffsetDateTime.now());
    }

    /**
     * Promotes a user to Super User, granting SUPER_USER role in all companies.
     *
     * @param actorId the user performing the promotion
     * @param targetId the user to promote
     */
    @Transactional
    @PreAuthorize("hasRole('SUPER_USER')")
    @Audited(action = "SUPER_USER_PROMOTED", entityType = "User", entityClass = User.class)
    public void promoteToSuperUser(Long actorId, Long targetId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException("Actor not found: " + actorId));
        if (!Boolean.TRUE.equals(actor.getIsSuperUser())) {
            throw new IllegalStateException("Only Super Users can promote other users");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + targetId));
        target.setIsSuperUser(true);
        userRepository.save(target);

        List<Company> allCompanies = companyRepository.findAll();
        for (Company company : allCompanies) {
            if (!userCompanyRoleRepository.existsByUserIdAndCompanyId(
                    targetId, company.getId())) {
                UserCompanyRole assignment = UserCompanyRole.builder()
                        .user(target)
                        .company(company)
                        .role(Role.SUPER_USER)
                        .grantedBy(actor)
                        .isActive(true)
                        .build();
                userCompanyRoleRepository.save(assignment);
            }
        }
    }

    /**
     * Synchronises a user's permission set for a specific company+context.
     *
     * @param userId the user identifier
     * @param companyId the company identifier
     * @param lovContextId the LOV context identifier
     * @param desiredPermissions the set of permission strings to apply
     * @param grantedByUserId the user granting the permissions (may be {@code null})
     */
    @Transactional
    @PreAuthorize("hasRole('SUPER_USER')")
    public void bulkSetPermissions(Long userId, Long companyId, Long lovContextId,
            Set<String> desiredPermissions, Long grantedByUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        LovContext lovContext = lovContextRepository.findById(lovContextId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "LOV context not found: " + lovContextId));
        User grantedBy = grantedByUserId != null
                ? userRepository.getReferenceById(grantedByUserId) : null;

        List<UserContextPermission> existing = userContextPermissionRepository
                .findByUserIdAndCompanyIdAndLovContextId(userId, companyId, lovContextId);

        Set<String> existingKeys = existing.stream()
                .map(UserContextPermission::getPermission)
                .collect(Collectors.toSet());

        for (UserContextPermission ucp : existing) {
            if (!desiredPermissions.contains(ucp.getPermission())) {
                userContextPermissionRepository.delete(ucp);
                auditService.log("PERMISSION_REVOKED", "UserContextPermission",
                        String.valueOf(userId), null,
                        "{\"permission\":\"" + ucp.getPermission()
                                + "\",\"companyId\":" + companyId
                                + ",\"lovContextId\":" + lovContextId + "}",
                        companyId);
            }
        }

        for (String perm : desiredPermissions) {
            if (!existingKeys.contains(perm)) {
                UserContextPermission newPerm = UserContextPermission.builder()
                        .user(user)
                        .company(companyRepository.getReferenceById(companyId))
                        .lovContext(lovContext)
                        .permission(perm)
                        .grantedBy(grantedBy)
                        .build();
                userContextPermissionRepository.save(newPerm);
                auditService.log("PERMISSION_GRANTED", "UserContextPermission",
                        String.valueOf(userId), null,
                        "{\"permission\":\"" + perm
                                + "\",\"companyId\":" + companyId
                                + ",\"lovContextId\":" + lovContextId + "}",
                        companyId);
            }
        }
    }

    /**
     * Retrieves the effective permissions for a user in a specific company and context.
     *
     * @param userId the user identifier
     * @param companyId the company identifier
     * @param lovContextId the LOV context identifier
     * @return the set of permission strings
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_USER')")
    public Set<String> getUserPermissions(Long userId, Long companyId, Long lovContextId) {
        return userContextPermissionRepository
                .findPermissionsByUserIdAndCompanyIdAndLovContextId(userId, companyId, lovContextId);
    }

    private void guardLastSuperUser(Long targetId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + targetId));
        if (Boolean.TRUE.equals(target.getIsSuperUser()) && Boolean.TRUE.equals(target.getIsActive())) {
            long remaining = userRepository.findAll().stream()
                    .filter(u -> !u.getId().equals(targetId)
                            && Boolean.TRUE.equals(u.getIsSuperUser())
                            && Boolean.TRUE.equals(u.getIsActive()))
                    .count();
            if (remaining == 0) {
                throw new IllegalStateException("Cannot remove the last active Super User");
            }
        }
    }

    /** Exception thrown when a user is not found. */
    public static class UserNotFoundException extends RuntimeException {
        /**
         * Creates a new UserNotFoundException.
         *
         * @param message the detail message
         */
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    /** Exception thrown when a user is already assigned to a company. */
    public static class UserAlreadyAssignedException extends RuntimeException {
        /**
         * Creates a new UserAlreadyAssignedException.
         *
         * @param message the detail message
         */
        public UserAlreadyAssignedException(String message) {
            super(message);
        }
    }

    /** Exception thrown when a user is not assigned to a company. */
    public static class UserNotAssignedException extends RuntimeException {
        /**
         * Creates a new UserNotAssignedException.
         *
         * @param message the detail message
         */
        public UserNotAssignedException(String message) {
            super(message);
        }
    }
}
