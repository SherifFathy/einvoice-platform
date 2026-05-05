package com.einvoice.security.tenant;

import java.util.UUID;

/** Javadoc. */
public final class TenantContext {

    /** Javadoc. */
    public enum Mode {
        ADMIN_MODE,
        OPERATIONAL_MODE
    }

    private static final ThreadLocal<Holder> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Holder holder) {
        CURRENT.set(holder);
    }

    public static Holder current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static UUID getUserId() {
        Holder h = current();
        return h != null ? h.userId : null;
    }

    public static UUID getCompanyId() {
        Holder h = current();
        return h != null ? h.companyId : null;
    }

    public static Short getAuthorityEnvironmentId() {
        Holder h = current();
        return h != null ? h.authorityEnvironmentId : null;
    }

    public static String getAuthority() {
        Holder h = current();
        return h != null ? h.authority : null;
    }

    public static String getEnvironment() {
        Holder h = current();
        return h != null ? h.environment : null;
    }

    public static Mode getMode() {
        Holder h = current();
        return h != null ? h.mode : null;
    }

    public static boolean isSuperUser() {
        Holder h = current();
        return h != null && h.isSuperUser;
    }

    public record Holder(
            UUID userId,
            UUID companyId,
            Short authorityEnvironmentId,
            String authority,
            String environment,
            Mode mode,
            boolean isSuperUser,
            long issuedAt,
            String jti
    ) {}
}
