package com.einvoice.security.permission;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

/** Javadoc. */
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PermissionCache {

    private final Map<CacheKey, Set<String>> entries = new HashMap<>();

    /**
     * Javadoc.
     * @param userId user identifier
     * @param companyId company identifier
     * @param authEnvId authority environment id
     * @param transactionType transaction type
     * @param loader permission loader
     * @return permission set
     */
    public Set<String> getOrCompute(UUID userId, UUID companyId, short authEnvId,
            String transactionType, Supplier<Set<String>> loader) {
        return entries.computeIfAbsent(
                new CacheKey(userId, companyId, authEnvId, transactionType),
                k -> loader.get());
    }

    private record CacheKey(UUID userId, UUID companyId, short authEnvId,
                            String transactionType) {}
}
