package com.einvoice.core.error;

/** Thrown when no active authority-environment row matches the requested pair. */
public class AuthorityEnvironmentNotFoundException extends RuntimeException {

    public static final String CODE = "AUTHORITY_ENVIRONMENT_NOT_FOUND";

    private final String authority;
    private final String environment;

    /** Constructs a new exception for the given authority and environment.
     * @param authority the authority
     * @param environment the environment
     */
    public AuthorityEnvironmentNotFoundException(String authority, String environment) {
        super("No active authority environment found for: " + authority + " / " + environment);
        this.authority = authority;
        this.environment = environment;
    }

    public String getCode() {
        return CODE;
    }

    public String getAuthority() {
        return authority;
    }

    public String getEnvironment() {
        return environment;
    }
}
