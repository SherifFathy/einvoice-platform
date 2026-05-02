package com.einvoice.zatca.validation;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.service.validation.ValidationError;
import com.einvoice.core.service.validation.ValidationLayer;
import com.einvoice.core.service.validation.ValidationRule;
import com.einvoice.core.service.validation.ValidationSeverity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ZATCA-specific business rule validations for invoice compliance.
 */
@Component
public class ZatcaComplianceRules implements ValidationRule {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<ValidationError> validate(Invoice invoice, Authority authority) {
        if (authority != Authority.ZATCA) {
            return List.of();
        }
        List<ValidationError> errors = new ArrayList<>();
        validateIssueDateNotInFuture(invoice, errors);
        validateSupplyDates(invoice, errors);
        validateSubtypeFlags(invoice, errors);
        validateSellerAddress(invoice, errors);
        validateBuyerVatForExports(invoice, errors);
        validateCreditNoteReference(invoice, errors);
        return errors;
    }

    private void validateIssueDateNotInFuture(Invoice invoice, List<ValidationError> errors) {
        if (invoice.getIssueDate() != null && invoice.getIssueDate().isAfter(LocalDate.now())) {
            errors.add(new ValidationError(
                    ValidationLayer.COMPLIANCE, Authority.ZATCA, "ZATCA-001",
                    "issueDate", "Issue date must not be in the future (BR-KSA-04)",
                    ValidationSeverity.ERROR));
        }
    }

    private void validateSupplyDates(Invoice invoice, List<ValidationError> errors) {
        if (invoice.getSupplyDate() != null && invoice.getSupplyEndDate() != null) {
            if (!invoice.getSupplyEndDate().isAfter(invoice.getSupplyDate())) {
                errors.add(new ValidationError(
                        ValidationLayer.COMPLIANCE, Authority.ZATCA, "ZATCA-002",
                        "supplyEndDate",
                        "Supply end date must be after supply date (BR-KSA-15)",
                        ValidationSeverity.ERROR));
            }
        }
    }

    private void validateSubtypeFlags(Invoice invoice, List<ValidationError> errors) {
        String flags = invoice.getSubtypeFlags();
        if (flags == null || flags.isBlank() || "{}".equals(flags.trim())) {
            return;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(flags);
            boolean isExport = node.path("exports").asBoolean(false);
            boolean isDomestic = node.path("domestic").asBoolean(false);
            if (isExport && isDomestic) {
                errors.add(new ValidationError(
                        ValidationLayer.COMPLIANCE, Authority.ZATCA, "ZATCA-003",
                        "subtypeFlags",
                        "Invoice cannot be both export and domestic (BR-KSA-07)",
                        ValidationSeverity.ERROR));
            }
        } catch (Exception ignored) {
        }
    }

    private void validateSellerAddress(Invoice invoice, List<ValidationError> errors) {
        if (invoice.getBranch() == null) {
            return;
        }
        var branch = invoice.getBranch();
        if (branch.getStreet() == null || branch.getStreet().isBlank()) {
            errors.add(new ValidationError(
                    ValidationLayer.COMPLIANCE, Authority.ZATCA, "ZATCA-004",
                    "branch.street",
                    "Seller address street is required (BR-KSA-09)",
                    ValidationSeverity.ERROR));
        }
        if (branch.getCity() == null || branch.getCity().isBlank()) {
            errors.add(new ValidationError(
                    ValidationLayer.COMPLIANCE, Authority.ZATCA, "ZATCA-004",
                    "branch.city",
                    "Seller address city is required (BR-KSA-09)",
                    ValidationSeverity.ERROR));
        }
    }

    private void validateBuyerVatForExports(Invoice invoice, List<ValidationError> errors) {
        String flags = invoice.getSubtypeFlags();
        boolean isExport = false;
        if (flags != null && !flags.isBlank()) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(flags);
                isExport = node.path("exports").asBoolean(false);
            } catch (Exception ignored) {
            }
        }
        if (!isExport) {
            return;
        }
        if (invoice.getBuyer() == null) {
            return;
        }
        String buyerVat = invoice.getBuyer().getVatNumber();
        if (buyerVat == null || buyerVat.isBlank()) {
            errors.add(new ValidationError(
                    ValidationLayer.COMPLIANCE, Authority.ZATCA, "ZATCA-005",
                    "buyer.vatNumber",
                    "Buyer VAT number is required for export invoices (BR-KSA-46)",
                    ValidationSeverity.ERROR));
        }
    }

    private void validateCreditNoteReference(Invoice invoice, List<ValidationError> errors) {
        if (invoice.getType() != InvoiceType.CREDIT_NOTE && invoice.getType() != InvoiceType.DEBIT_NOTE) {
            return;
        }
        boolean hasInternalRef = invoice.getOriginalInvoice() != null;
        boolean hasExternalRef = invoice.getExternalInvoiceReference() != null
                && !invoice.getExternalInvoiceReference().isBlank();
        if (!hasInternalRef && !hasExternalRef) {
            errors.add(new ValidationError(
                    ValidationLayer.COMPLIANCE, Authority.ZATCA, "ZATCA-006",
                    "originalInvoice",
                    "Credit/debit note must reference an invoice (BR-KSA-56)",
                    ValidationSeverity.ERROR));
        }
    }
}
