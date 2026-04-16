package com.einvoice.core.service;

import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.RefreshToken;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.UserEnvironmentPermission;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.exception.CompanyDeactivatedException;
import com.einvoice.core.exception.EnvironmentAccessDeniedException;
import com.einvoice.core.exception.InvalidCredentialsException;
import com.einvoice.core.exception.InvalidRefreshTokenException;
import com.einvoice.core.exception.NoRoleInCompanyException;
import com.einvoice.core.repository.RefreshTokenRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserEnvironmentPermissionRepository;
import com.einvoice.core.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service handling user authentication, token management, and company switching. */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserCompanyRoleRepository userCompanyRoleRepository;
    private final UserEnvironmentPermissionRepository userEnvironmentPermissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * Creates an AuthenticationService with the required dependencies.
     *
     * @param userRepository the user repository
     * @param userCompanyRoleRepository the user-company role assignment repository
     * @param userEnvironmentPermissionRepository the environment permission repository
     * @param refreshTokenRepository the refresh token repository
     * @param tokenService the token generation service (SPI)
     * @param passwordEncoder the password encoder for verifying credentials
     * @param auditService the audit service for logging auth events
     */
    public AuthenticationService(UserRepository userRepository,
            UserCompanyRoleRepository userCompanyRoleRepository,
            UserEnvironmentPermissionRepository userEnvironmentPermissionRepository,
            RefreshTokenRepository refreshTokenRepository,
            TokenService tokenService,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.userCompanyRoleRepository = userCompanyRoleRepository;
        this.userEnvironmentPermissionRepository = userEnvironmentPermissionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Authenticates a user with email and password, issuing access and refresh tokens.
     *
     * @param email the user's email address
     * @param password the plain-text password
     * @return authentication result containing tokens and user context
     * @throws InvalidCredentialsException if credentials are invalid or user is inactive
     * @throws CompanyDeactivatedException if the user's primary company is deactivated
     */
    @Transactional
    public AuthResult login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    auditService.log("auth.login_failed", "User", email,
                            null, "Invalid credentials - user not found", null);
                    return new InvalidCredentialsException("Invalid credentials");
                });

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            auditService.log("auth.login_failed", "User", String.valueOf(user.getId()),
                    null, "Account deactivated", null);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.log("auth.login_failed", "User", String.valueOf(user.getId()),
                    null, "Invalid credentials - wrong password", null);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        List<UserCompanyRole> roles = userCompanyRoleRepository
                .findByUserIdAndIsActiveTrue(user.getId());

        if (roles.isEmpty()) {
            auditService.log("auth.login_failed", "User", String.valueOf(user.getId()),
                    null, "No active company assignments", null);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        UserCompanyRole primaryRole = roles.get(0);
        Company activeCompany = primaryRole.getCompany();

        if (!Boolean.TRUE.equals(activeCompany.getIsActive())) {
            auditService.log("auth.login_failed", "User", String.valueOf(user.getId()),
                    null, "Company deactivated: " + activeCompany.getId(),
                    activeCompany.getId());
            throw new CompanyDeactivatedException("Company is deactivated");
        }

        List<String> permittedEnvironments = getPermittedEnvironments(primaryRole.getId());
        List<Map<String, Object>> availableCompanies = buildAvailableCompanies(roles);

        String accessToken = tokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), activeCompany.getId(),
                primaryRole.getRole().name(), permittedEnvironments, availableCompanies, null);

        String refreshTokenValue = tokenService.generateRefreshToken(
                user.getId(), activeCompany.getId());
        RefreshToken refreshToken = persistRefreshToken(user, refreshTokenValue);

        auditService.log("auth.login_success", "User", String.valueOf(user.getId()),
                null, null, activeCompany.getId());

        return new AuthResult(accessToken, refreshToken.getToken(),
                tokenService.getAccessTokenExpirySeconds(), user,
                activeCompany.getId(), primaryRole.getRole().name(),
                permittedEnvironments, availableCompanies, null);
    }

    /**
     * Switches the user's active company context after password verification.
     *
     * @param userId the authenticated user's ID
     * @param targetCompanyId the company to switch to
     * @param password the user's current password for verification
     * @return authentication result with updated company context
     * @throws InvalidCredentialsException if the password is incorrect
     * @throws NoRoleInCompanyException if the user has no role in the target company
     * @throws CompanyDeactivatedException if the target company is deactivated
     */
    @Transactional
    public AuthResult switchCompany(Long userId, Long targetCompanyId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.log("auth.switch_company_failed", "User",
                    String.valueOf(userId),
                    null, "Invalid password for company switch", targetCompanyId);
            throw new InvalidCredentialsException("Invalid password");
        }

        UserCompanyRole targetRole = userCompanyRoleRepository
                .findByUserIdAndCompanyId(userId, targetCompanyId)
                .orElseThrow(() -> new NoRoleInCompanyException(
                        "User has no role in company " + targetCompanyId));

        if (!Boolean.TRUE.equals(targetRole.getIsActive())) {
            throw new NoRoleInCompanyException(
                    "User role is inactive in company " + targetCompanyId);
        }

        Company targetCompany = targetRole.getCompany();
        if (!Boolean.TRUE.equals(targetCompany.getIsActive())) {
            throw new CompanyDeactivatedException("Company is deactivated");
        }

        List<UserCompanyRole> allRoles = userCompanyRoleRepository
                .findByUserIdAndIsActiveTrue(userId);

        List<String> permittedEnvironments = getPermittedEnvironments(targetRole.getId());
        List<Map<String, Object>> availableCompanies = buildAvailableCompanies(allRoles);

        String accessToken = tokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), targetCompany.getId(),
                targetRole.getRole().name(), permittedEnvironments, availableCompanies, null);

        String refreshTokenValue = tokenService.generateRefreshToken(
                user.getId(), targetCompany.getId());
        RefreshToken refreshToken = persistRefreshToken(user, refreshTokenValue);

        auditService.log("auth.switch_company", "User", String.valueOf(userId),
                null, "Switched to company " + targetCompanyId, targetCompanyId);

        return new AuthResult(accessToken, refreshToken.getToken(),
                tokenService.getAccessTokenExpirySeconds(), user,
                targetCompany.getId(), targetRole.getRole().name(),
                permittedEnvironments, availableCompanies, null);
    }

    /**
     * Selects an active environment for the user, validates permissions,
     * and re-issues JWT with the environment claim.
     *
     * @param userId the authenticated user's ID
     * @param companyId the active company ID
     * @param environmentName the environment to select
     * @return authentication result with new JWT containing the active environment
     * @throws IllegalArgumentException if the environment name is invalid
     * @throws NoRoleInCompanyException if the user has no role in the company
     * @throws EnvironmentAccessDeniedException if the user lacks permission
     */
    @Transactional
    public AuthResult selectEnvironment(Long userId, Long companyId, String environmentName) {
        Environment environment;
        try {
            environment = Environment.valueOf(environmentName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid environment: " + environmentName);
        }

        UserCompanyRole role = userCompanyRoleRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new NoRoleInCompanyException(
                        "User has no role in company " + companyId));

        boolean hasAccess = userEnvironmentPermissionRepository
                .existsByUserCompanyRoleIdAndEnvironment(role.getId(), environment);

        if (!hasAccess) {
            throw new EnvironmentAccessDeniedException(
                    "User does not have permission for environment " + environmentName
                            + " in company " + companyId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        List<UserCompanyRole> allRoles = userCompanyRoleRepository
                .findByUserIdAndIsActiveTrue(userId);
        List<String> permittedEnvironments = getPermittedEnvironments(role.getId());
        List<Map<String, Object>> availableCompanies = buildAvailableCompanies(allRoles);

        String accessToken = tokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), companyId,
                role.getRole().name(), permittedEnvironments, availableCompanies,
                environmentName);

        String refreshTokenValue = tokenService.generateRefreshToken(
                user.getId(), companyId);
        RefreshToken refreshToken = persistRefreshToken(user, refreshTokenValue);

        auditService.log("auth.select_environment", "User", String.valueOf(userId),
                null, "Selected environment " + environmentName, companyId);

        return new AuthResult(accessToken, refreshToken.getToken(),
                tokenService.getAccessTokenExpirySeconds(), user,
                companyId, role.getRole().name(),
                permittedEnvironments, availableCompanies, environmentName);
    }

    /**
     * Refreshes an access token using a valid refresh token (rotation pattern).
     * Preserves the user's active company context from the original session.
     *
     * @param oldRefreshTokenValue the existing refresh token to rotate
     * @return authentication result with new tokens and preserved company context
     * @throws InvalidRefreshTokenException if the refresh token is invalid, revoked, or expired
     */
    @Transactional
    public AuthResult refresh(String oldRefreshTokenValue) {
        RefreshToken oldToken = refreshTokenRepository.findByToken(oldRefreshTokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        if (oldToken.isRevoked() || oldToken.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token is revoked or expired");
        }

        oldToken.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(oldToken);

        User user = oldToken.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InvalidCredentialsException("User account is deactivated");
        }

        List<UserCompanyRole> roles = userCompanyRoleRepository
                .findByUserIdAndIsActiveTrue(user.getId());

        if (roles.isEmpty()) {
            throw new InvalidCredentialsException("No active company assignments");
        }

        Long preservedCompanyId = tokenService.getActiveCompanyIdFromToken(
                oldRefreshTokenValue);
        UserCompanyRole activeRole = findRoleForCompany(roles, preservedCompanyId)
                .orElse(roles.get(0));
        Company activeCompany = activeRole.getCompany();

        List<String> permittedEnvironments = getPermittedEnvironments(activeRole.getId());
        List<Map<String, Object>> availableCompanies = buildAvailableCompanies(roles);

        String accessToken = tokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), activeCompany.getId(),
                activeRole.getRole().name(), permittedEnvironments, availableCompanies, null);

        String newRefreshTokenValue = tokenService.generateRefreshToken(
                user.getId(), activeCompany.getId());
        RefreshToken newRefreshToken = persistRefreshToken(user, newRefreshTokenValue);

        return new AuthResult(accessToken, newRefreshToken.getToken(),
                tokenService.getAccessTokenExpirySeconds(), user,
                activeCompany.getId(), activeRole.getRole().name(),
                permittedEnvironments, availableCompanies, null);
    }

    /**
     * Revokes a refresh token, effectively logging out the session.
     *
     * @param refreshTokenValue the refresh token to revoke
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(token);
            auditService.log("auth.logout", "User",
                    String.valueOf(token.getUser().getId()),
                    null, null, null);
        });
    }

    private Optional<UserCompanyRole> findRoleForCompany(
            List<UserCompanyRole> roles, Long companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return roles.stream()
                .filter(r -> companyId.equals(r.getCompany().getId()))
                .findFirst();
    }

    private RefreshToken persistRefreshToken(User user, String tokenValue) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(OffsetDateTime.now()
                        .plusSeconds(tokenService.getRefreshTokenExpirySeconds()))
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private List<String> getPermittedEnvironments(Long userCompanyRoleId) {
        return userEnvironmentPermissionRepository
                .findByUserCompanyRoleId(userCompanyRoleId).stream()
                .map(uep -> uep.getEnvironment().name())
                .toList();
    }

    private List<Map<String, Object>> buildAvailableCompanies(List<UserCompanyRole> roles) {
        return roles.stream()
                .filter(r -> Boolean.TRUE.equals(r.getCompany().getIsActive()))
                .map(r -> Map.<String, Object>of(
                        "id", r.getCompany().getId(),
                        "name", r.getCompany().getNameEn()))
                .toList();
    }

    /** Authentication result containing tokens and user context. */
    public static class AuthResult {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresIn;
        public final User user;
        public final Long activeCompanyId;
        public final String role;
        public final List<String> permittedEnvironments;
        public final List<Map<String, Object>> availableCompanies;
        public final String activeEnvironment;

        /**
         * Creates an AuthResult.
         *
         * @param accessToken the JWT access token
         * @param refreshToken the refresh token string
         * @param expiresIn access token expiry in seconds
         * @param user the authenticated user entity
         * @param activeCompanyId the active company ID
         * @param role the user's role in the active company
         * @param permittedEnvironments list of permitted environment names
         * @param availableCompanies list of available company maps
         * @param activeEnvironment the selected environment, or null
         */
        public AuthResult(String accessToken, String refreshToken, long expiresIn,
                User user, Long activeCompanyId, String role,
                List<String> permittedEnvironments,
                List<Map<String, Object>> availableCompanies,
                String activeEnvironment) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.user = user;
            this.activeCompanyId = activeCompanyId;
            this.role = role;
            this.permittedEnvironments = permittedEnvironments;
            this.availableCompanies = availableCompanies;
            this.activeEnvironment = activeEnvironment;
        }
    }
}
