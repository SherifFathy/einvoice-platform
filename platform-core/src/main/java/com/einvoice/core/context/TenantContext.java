package com.einvoice.core.context;

/** ThreadLocal holder for the current tenant and LOV context identifiers. */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_LOV_CONTEXT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Long getCurrentTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getLovContextId() {
        return CURRENT_LOV_CONTEXT.get();
    }

    public static void setLovContextId(Long lovContextId) {
        CURRENT_LOV_CONTEXT.set(lovContextId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_LOV_CONTEXT.remove();
    }
}
