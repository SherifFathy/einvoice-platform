package com.einvoice.api.session;

import com.einvoice.api.session.dto.SessionContextResponse;
import com.einvoice.security.tenant.TenantContext;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Time-based cache for session context responses keyed by user and JWT ID. */
@Component
public class SessionContextCache {

    private final ConcurrentHashMap<CacheKey, Entry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public SessionContextCache(com.einvoice.security.jwt.JwtProperties jwtProperties) {
        this.ttlMillis = jwtProperties.getTtlSeconds() * 1000L;
    }

    /**
     * Returns a cached context or computes, caches, and returns a new one.
     *
     * @param holder the tenant context holder
     * @param supplier the response supplier
     * @return the session context response
     */
    public SessionContextResponse getOrCompute(TenantContext.Holder holder,
            Supplier<SessionContextResponse> supplier) {
        CacheKey key = new CacheKey(holder.userId(), holder.jti());
        Entry existing = cache.get(key);
        if (existing != null && !existing.isExpired()) {
            return existing.response;
        }
        SessionContextResponse response = supplier.get();
        cache.put(key, new Entry(response, System.currentTimeMillis() + ttlMillis));
        evictExpired();
        return response;
    }

    /**
     * Invalidates every cached session context. Called when shared data that the
     * context derives from changes (e.g. a company is created, renamed, or
     * deactivated) so all active sessions recompute their company list on the
     * next request instead of waiting for the per-token TTL to lapse.
     */
    public void invalidateAll() {
        cache.clear();
    }

    void clearForTesting() {
        cache.clear();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    record CacheKey(UUID userId, String jti) {}

    record Entry(SessionContextResponse response, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
