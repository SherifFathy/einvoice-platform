package com.einvoice.core.context;

/** ThreadLocal holder for the current environment. Set during auth/select-environment flow. */
public final class EnvironmentContext {

    private static final ThreadLocal<String> CURRENT_ENVIRONMENT = new ThreadLocal<>();

    private EnvironmentContext() {
    }

    public static String getCurrentEnvironment() {
        return CURRENT_ENVIRONMENT.get();
    }

    public static void setCurrentEnvironment(String environment) {
        CURRENT_ENVIRONMENT.set(environment);
    }

    public static void clear() {
        CURRENT_ENVIRONMENT.remove();
    }
}
