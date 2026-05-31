package com.einvoice.core.error;

import java.util.UUID;

/** Javadoc. */
public class NoCertificateConfiguredException extends RuntimeException {
    public static final String CODE = "NO_CERTIFICATE_CONFIGURED";
    private final UUID companyId;
    private final Short authorityEnvironmentId;

    /**
     * Construct the exception.
     *
     * @param message                the error message
     * @param companyId              the company identifier
     * @param authorityEnvironmentId the authority environment identifier
     */
    public NoCertificateConfiguredException(String message, UUID companyId, Short authorityEnvironmentId) {
        super(message);
        this.companyId = companyId;
        this.authorityEnvironmentId = authorityEnvironmentId;
    }

    public String getCode() {
        return CODE;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public Short getAuthorityEnvironmentId() {
        return authorityEnvironmentId;
    }
}
