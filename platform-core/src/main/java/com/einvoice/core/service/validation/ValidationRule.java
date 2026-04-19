package com.einvoice.core.service.validation;

/** Interface for invoice validation rules. */
public interface ValidationRule {

    /**
     * Validates an invoice against this rule.
     *
     * @param invoice the invoice to validate
     * @param authority the target authority
     * @return list of validation errors found
     */
    java.util.List<ValidationError> validate(
            com.einvoice.core.domain.Invoice invoice,
            com.einvoice.core.domain.enums.Authority authority);
}
