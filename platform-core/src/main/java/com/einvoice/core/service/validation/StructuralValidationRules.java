package com.einvoice.core.service.validation;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.InvoiceType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Validates structural integrity of invoice data. */
@Component
public class StructuralValidationRules implements ValidationRule {

    @Override
    public List<ValidationError> validate(Invoice invoice, Authority authority) {
        List<ValidationError> errors = new ArrayList<>();
        validateRequiredFields(invoice, errors);
        validateAtLeastOneLine(invoice, errors);
        validateBuyerVatForB2b(invoice, errors);
        validateCreditDebitNoteReference(invoice, errors);
        return errors;
    }

    private void validateRequiredFields(Invoice invoice, List<ValidationError> errors) {
        if (invoice.getIssueDate() == null) {
            errors.add(new ValidationError(
                    ValidationLayer.STRUCTURAL, null, "STRUCT-001",
                    "issueDate", "Issue date is required", ValidationSeverity.ERROR));
        }
        if (invoice.getCurrency() == null || invoice.getCurrency().isBlank()) {
            errors.add(new ValidationError(
                    ValidationLayer.STRUCTURAL, null, "STRUCT-001",
                    "currency", "Currency is required", ValidationSeverity.ERROR));
        }
        if (invoice.getBranch() == null) {
            errors.add(new ValidationError(
                    ValidationLayer.STRUCTURAL, null, "STRUCT-001",
                    "branch", "Branch is required", ValidationSeverity.ERROR));
        }
        if (invoice.getCreatedBy() == null) {
            errors.add(new ValidationError(
                    ValidationLayer.STRUCTURAL, null, "STRUCT-001",
                    "createdBy", "Created by user is required", ValidationSeverity.ERROR));
        }
    }

    private void validateAtLeastOneLine(Invoice invoice, List<ValidationError> errors) {
        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            errors.add(new ValidationError(
                    ValidationLayer.STRUCTURAL, null, "STRUCT-002",
                    "lines", "Invoice must have at least one line item", ValidationSeverity.ERROR));
        }
    }

    private void validateBuyerVatForB2b(Invoice invoice, List<ValidationError> errors) {
        if (invoice.getBuyer() != null && invoice.getBuyer().getCustomerType() != null
                && "B2B".equals(invoice.getBuyer().getCustomerType().name())) {
            String buyerData = invoice.getBuyerData();
            if (buyerData == null || buyerData.isBlank() || !buyerData.contains("vatNumber")) {
                errors.add(new ValidationError(
                        ValidationLayer.STRUCTURAL, null, "STRUCT-003",
                        "buyer.vatNumber", "Buyer VAT number is required for B2B invoices",
                        ValidationSeverity.ERROR));
            }
        }
    }

    private void validateCreditDebitNoteReference(Invoice invoice, List<ValidationError> errors) {
        InvoiceType type = invoice.getType();
        if (type == InvoiceType.CREDIT_NOTE || type == InvoiceType.DEBIT_NOTE) {
            boolean hasInternalRef = invoice.getOriginalInvoice() != null;
            boolean hasExternalRef = invoice.getExternalInvoiceReference() != null
                    && !invoice.getExternalInvoiceReference().isBlank();
            if (!hasInternalRef && !hasExternalRef) {
                errors.add(new ValidationError(
                        ValidationLayer.STRUCTURAL, null, "STRUCT-004",
                        "originalInvoice",
                        "Credit/debit notes must reference either an internal invoice or provide an external reference",
                        ValidationSeverity.ERROR));
            }
        }
    }
}
