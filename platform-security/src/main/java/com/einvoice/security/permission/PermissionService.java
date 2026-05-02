package com.einvoice.security.permission;

import com.einvoice.core.context.LovContextResolver;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.Permission;
import com.einvoice.core.repository.UserContextPermissionRepository;
import com.einvoice.core.repository.UserRepository;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Resolves and caches the effective permissions for the current user. */
@Service
public class PermissionService implements LovContextResolver {

    private final UserContextPermissionRepository userContextPermissionRepository;
    private final UserRepository userRepository;
    private final PermissionCache permissionCache;

    /**
     * Creates the permission service.
     *
     * @param userContextPermissionRepository the user-context permission repository
     * @param userRepository the user repository
     * @param permissionCache the request-scoped permission cache
     */
    public PermissionService(
            UserContextPermissionRepository userContextPermissionRepository,
            UserRepository userRepository,
            PermissionCache permissionCache) {
        this.userContextPermissionRepository = userContextPermissionRepository;
        this.userRepository = userRepository;
        this.permissionCache = permissionCache;
    }

    @Override
    public Long resolveEffectiveLovContextId() {
        Long userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (Boolean.TRUE.equals(user.getIsSuperUser())) {
            return null;
        }
        return TenantContext.getLovContextId();
    }

    public Set<Permission> getPermissions(Long userId, Long companyId, Long lovContextId) {
        return permissionCache.getOrCompute(userId, companyId, lovContextId,
                () -> loadFromRepository(userId, companyId, lovContextId));
    }

    private Set<Permission> loadFromRepository(Long userId, Long companyId, Long lovContextId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (Boolean.TRUE.equals(user.getIsSuperUser())) {
            return EnumSet.allOf(Permission.class);
        }

        Set<String> permissionKeys = userContextPermissionRepository
                .findPermissionsByUserIdAndCompanyIdAndLovContextId(userId, companyId, lovContextId);

        if (permissionKeys == null || permissionKeys.isEmpty()) {
            return Collections.emptySet();
        }

        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (String key : permissionKeys) {
            try {
                permissions.add(Permission.valueOf(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return permissions;
    }

    /**
     * Returns the effective permissions for the current authenticated user.
     *
     * @return the set of permissions granted to the current user
     */
    public Set<Permission> getCurrentPermissions() {
        Long userId = getCurrentUserId();
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = TenantContext.getLovContextId();
        return getPermissions(userId, companyId, lovContextId);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("No authenticated user found");
    }
}
