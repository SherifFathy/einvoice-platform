package com.einvoice.api.user;

import com.einvoice.api.user.dto.PermissionRequest;
import com.einvoice.api.user.dto.UserCompanyResponse;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.UserEnvironmentPermission;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.service.EnvironmentService;
import com.einvoice.core.service.UserService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for Company Admin user and permission management. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class UserController {

    private final UserService userService;
    private final EnvironmentService environmentService;
    private final UserCompanyRoleRepository userCompanyRoleRepository;

    /**
     * Creates the controller.
     *
     * @param userService the user service
     * @param environmentService the environment service
     * @param userCompanyRoleRepository the user-company role repository
     */
    public UserController(UserService userService,
            EnvironmentService environmentService,
            UserCompanyRoleRepository userCompanyRoleRepository) {
        this.userService = userService;
        this.environmentService = environmentService;
        this.userCompanyRoleRepository = userCompanyRoleRepository;
    }

    /**
     * Lists users assigned to a company.
     *
     * @param companyId the company identifier
     * @return list of users with their roles and permissions
     */
    @GetMapping("/companies/{companyId}/users")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<UserCompanyResponse>> listUsers(@PathVariable Long companyId) {
        validateTenantAccess(companyId);
        List<UserCompanyRole> roles = userCompanyRoleRepository.findByCompanyId(companyId);
        List<UserCompanyResponse> response = roles.stream()
                .map(role -> toUserResponse(role, companyId))
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Assigns environment permissions to a user in the active company.
     *
     * @param id the user identifier
     * @param request the permission request with environments
     * @param authentication the current authentication
     * @return the updated user with permissions
     */
    @PostMapping("/users/{id}/permissions")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserCompanyResponse> assignPermissions(
            @PathVariable Long id, @Valid @RequestBody PermissionRequest request,
            Authentication authentication) {
        Long companyId = TenantContext.getCurrentTenantId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No active company context");
        }
        Long grantedByUserId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            grantedByUserId = userId;
        }
        List<UserEnvironmentPermission> permissions = environmentService.assignPermissions(
                id, companyId, request.environments(), grantedByUserId);
        UserCompanyRole role = userCompanyRoleRepository
                .findByUserIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User has no role in the active company"));
        List<String> envNames = permissions.stream()
                .map(uep -> uep.getEnvironment().name()).toList();
        return ResponseEntity.ok(new UserCompanyResponse(
                role.getUser().getId(), role.getUser().getName(),
                role.getUser().getEmail(), role.getRole().name(),
                role.getIsActive(), envNames));
    }

    /**
     * Activates a user's role in the active company.
     *
     * @param id the user identifier
     * @return the updated user
     */
    @PutMapping("/users/{id}/activate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserCompanyResponse> activateUser(@PathVariable Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No active company context");
        }
        UserCompanyRole role = userService.activateRole(id, companyId);
        return ResponseEntity.ok(toUserResponse(role, companyId));
    }

    /**
     * Deactivates a user's role in the active company.
     *
     * @param id the user identifier
     * @return the updated user
     */
    @PutMapping("/users/{id}/deactivate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserCompanyResponse> deactivateUser(@PathVariable Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No active company context");
        }
        UserCompanyRole role = userService.deactivateRole(id, companyId);
        return ResponseEntity.ok(toUserResponse(role, companyId));
    }

    /**
     * Lists all available environments.
     *
     * @return list of environment names
     */
    @GetMapping("/environments")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<String>> listEnvironments() {
        return ResponseEntity.ok(
                Arrays.stream(Environment.values()).map(Environment::name).toList());
    }

    private void validateTenantAccess(Long companyId) {
        boolean isSuperAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (isSuperAdmin) {
            return;
        }
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null && !tenantId.equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot access company " + companyId + " from current tenant context");
        }
    }

    private UserCompanyResponse toUserResponse(UserCompanyRole role, Long companyId) {
        List<String> envs = environmentService.getPermittedEnvironments(
                role.getUser().getId(), companyId);
        return new UserCompanyResponse(
                role.getUser().getId(), role.getUser().getName(),
                role.getUser().getEmail(), role.getRole().name(),
                role.getIsActive(), envs);
    }
}
