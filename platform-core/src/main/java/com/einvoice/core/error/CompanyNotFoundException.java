package com.einvoice.core.error;

/** Thrown when no active company matches the given tax number / registration number. */
public class CompanyNotFoundException extends RuntimeException {

    public static final String CODE = "COMPANY_NOT_FOUND";

    private final String registrationNumber;

    public CompanyNotFoundException(String registrationNumber) {
        super("Company not found for registration number: " + registrationNumber);
        this.registrationNumber = registrationNumber;
    }

    public String getCode() {
        return CODE;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }
}
