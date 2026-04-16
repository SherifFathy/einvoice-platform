package com.einvoice.security.rbac;

import com.einvoice.core.domain.enums.Role;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Maps roles to Spring Security authorities for @PreAuthorize method security. */
@Component
public class RolePermissions {

    private static final Map<Role, Set<String>> ROLE_AUTHORITIES;

    static {
        Map<Role, Set<String>> map = new HashMap<>();

        Set<String> viewer = new HashSet<>();
        viewer.add("READ");
        map.put(Role.VIEWER, Collections.unmodifiableSet(viewer));

        Set<String> accountant = new HashSet<>(viewer);
        accountant.add("CREATE");
        accountant.add("UPDATE");
        accountant.add("DELETE");
        map.put(Role.ACCOUNTANT, Collections.unmodifiableSet(accountant));

        Set<String> companyAdmin = new HashSet<>(accountant);
        companyAdmin.add("CONFIGURE");
        companyAdmin.add("MANAGE_USERS");
        map.put(Role.COMPANY_ADMIN, Collections.unmodifiableSet(companyAdmin));

        Set<String> superAdmin = new HashSet<>(companyAdmin);
        superAdmin.add("ADMIN");
        superAdmin.add("MANAGE_COMPANIES");
        map.put(Role.SUPER_ADMIN, Collections.unmodifiableSet(superAdmin));

        ROLE_AUTHORITIES = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the set of authority strings for a given role.
     *
     * @param role the role to look up
     * @return unmodifiable set of authority strings
     */
    public Set<String> getAuthorities(Role role) {
        return ROLE_AUTHORITIES.getOrDefault(role, Collections.emptySet());
    }

    /**
     * Checks if a role has a specific authority.
     *
     * @param role the role to check
     * @param authority the authority string to look for
     * @return true if the role includes the authority
     */
    public boolean hasAuthority(Role role, String authority) {
        return getAuthorities(role).contains(authority);
    }
}
