package com.einvoice.core.domain.enums;

/** Steps in the ZATCA onboarding process. */
public enum OnboardingStep {
    NOT_STARTED,
    CSR_GENERATED,
    COMPLIANCE_CSID_OBTAINED,
    TEST_INVOICES_SUBMITTED,
    PRODUCTION_CSID_OBTAINED
}
