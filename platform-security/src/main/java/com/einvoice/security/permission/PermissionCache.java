package com.einvoice.security.permission;

import com.einvoice.core.domain.enums.Permission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

/** Request-scoped cache for resolved permission sets. */
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PermissionCache {

    private final Map<CacheKey, Set<Permission>> entries = new HashMap<>();

    public Set<Permission> getOrCompute(Long userId, Long companyId, Long lovContextId,
            Supplier<Set<Permission>> loader) {
        return entries.computeIfAbsent(new CacheKey(userId, companyId, lovContextId),
                k -> loader.get());
    }

    private record CacheKey(Long userId, Long companyId, Long lovContextId) {}
}
