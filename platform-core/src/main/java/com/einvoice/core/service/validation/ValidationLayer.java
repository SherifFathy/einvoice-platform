package com.einvoice.core.service.validation;

/** Validation layers ordered by execution priority. */
public enum ValidationLayer {
    STRUCTURAL,
    ARITHMETIC,
    COMPLIANCE,
    READINESS
}
