package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.enums.Role;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing users and their company role assignments. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserCompanyRoleRepository userCompanyRoleRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a UserService with the required dependencies.
     *
     * @param userRepository the user repository
     * @param userCompanyRoleRepository the user-company role assignment repository
     * @param companyRepository the company repository for managed references
     * @param passwordEncoder the password encoder for hashing new user passwords
     */
    public UserService(UserRepository userRepository,
            UserCompanyRoleRepository userCompanyRoleRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userCompanyRoleRepository = userCompanyRoleRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
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
    @Audited(action = "user.assign_to_company", entityType = "UserCompanyRole")
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
    @Audited(action = "user.remove_from_company",
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
