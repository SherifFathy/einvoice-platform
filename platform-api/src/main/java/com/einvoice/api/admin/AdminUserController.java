package com.einvoice.api.admin;

import com.einvoice.api.admin.dto.AssignUserCompanyRequest;
import com.einvoice.api.admin.dto.BulkPermissionRequest;
import com.einvoice.api.admin.dto.CreateUserRequest;
import com.einvoice.api.admin.dto.ResetPasswordRequest;
import com.einvoice.api.admin.dto.UpdateUserRequest;
import com.einvoice.api.admin.dto.UserCompanyAssignment;
import com.einvoice.api.admin.dto.UserResponse;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.enums.Permission;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for Super Admin user management endpoints. */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('SUPER_USER')")
public class AdminUserController {

    private final UserService userService;
    private final UserCompanyRoleRepository userCompanyRoleRepository;

    public AdminUserController(UserService userService,
            UserCompanyRoleRepository userCompanyRoleRepository) {
        this.userService = userService;
        this.userCompanyRoleRepository = userCompanyRoleRepository;
    }

    /**
     * Lists all users.
     *
     * @return a list of user responses
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        List<User> users = userService.listAllUsers();
        List<UserResponse> response = users.stream()
                .map(this::toUserResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new user.
     *
     * @param request the user creation request
     * @return the created user response with HTTP 201 status
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.name(), request.email(), request.password());
        return ResponseEntity.created(URI.create("/api/admin/users/" + user.getId()))
                .body(toUserResponse(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        User user = userService.updateUser(id, request.name(), request.email());
        return ResponseEntity.ok(toUserResponse(user));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activateUser(@PathVariable Long id) {
        User user = userService.activateUser(id);
        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Deactivates a user account.
     *
     * @param id the user identifier
     * @param authentication the current authentication
     * @return the deactivated user response
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable Long id, Authentication authentication) {
        Long actorId = extractUserId(authentication);
        User user = userService.deactivateUser(actorId, id);
        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Soft-deletes a user.
     *
     * @param id the user identifier
     * @param authentication the current authentication
     * @return an empty response with HTTP 204 status
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        Long actorId = extractUserId(authentication);
        userService.deleteUser(actorId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Promotes a user to Super User.
     *
     * @param id the user identifier
     * @param authentication the current authentication
     * @return an empty response with HTTP 204 status
     */
    @PutMapping("/{id}/promote-super-user")
    public ResponseEntity<Void> promoteToSuperUser(
            @PathVariable Long id, Authentication authentication) {
        Long actorId = extractUserId(authentication);
        userService.promoteToSuperUser(actorId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Assigns a user to a company with a specific role.
     *
     * @param id the user identifier
     * @param request the assignment request
     * @param authentication the current authentication
     * @return an empty response with HTTP 201 status
     */
    @PostMapping("/{id}/companies")
    public ResponseEntity<Void> assignToCompany(
            @PathVariable Long id, @Valid @RequestBody AssignUserCompanyRequest request,
            Authentication authentication) {
        Long grantedByUserId = extractUserId(authentication);
        userService.assignToCompany(id, request.companyId(), request.role(), grantedByUserId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/companies/{companyId}")
    public ResponseEntity<Void> removeFromCompany(
            @PathVariable Long id, @PathVariable Long companyId) {
        userService.removeFromCompany(id, companyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets the effective permissions for a user in a specific company and context.
     *
     * @param id the user identifier
     * @param request the bulk permission request
     * @param authentication the current authentication
     * @return an empty response with HTTP 204 status
     */
    @PostMapping("/{id}/permissions")
    public ResponseEntity<Void> bulkSetPermissions(
            @PathVariable Long id, @Valid @RequestBody BulkPermissionRequest request,
            Authentication authentication) {
        Long grantedByUserId = extractUserId(authentication);
        Set<String> permissions = request.permissions() != null
                ? request.permissions().stream().map(Permission::name).collect(java.util.stream.Collectors.toSet())
                : Set.of();
        userService.bulkSetPermissions(id, request.companyId(), request.lovContextId(),
                permissions, grantedByUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<String>> getUserPermissions(
            @PathVariable Long id,
            @RequestParam Long companyId,
            @RequestParam Long lovContextId) {
        Set<String> permissions = userService.getUserPermissions(id, companyId, lovContextId);
        return ResponseEntity.ok(List.copyOf(permissions));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("No authenticated user principal found");
    }

    private UserResponse toUserResponse(User user) {
        List<UserCompanyRole> roles = userCompanyRoleRepository.findByUserId(user.getId());
        List<UserCompanyAssignment> companies = roles.stream()
                .map(r -> new UserCompanyAssignment(
                        r.getCompany().getId(),
                        r.getCompany().getNameEn(),
                        r.getRole().name(),
                        r.getIsActive()))
                .toList();
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getIsActive(), user.getIsSuperUser(),
                user.getCreatedAt(), companies);
    }
}
