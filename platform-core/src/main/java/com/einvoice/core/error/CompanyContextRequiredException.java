package com.einvoice.core.error;

/** Javadoc. */
public class CompanyContextRequiredException extends RuntimeException {

    public static final String CODE = "COMPANY_CONTEXT_REQUIRED";

    public CompanyContextRequiredException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
