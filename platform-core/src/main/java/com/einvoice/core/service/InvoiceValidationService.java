package com.einvoice.core.service;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.domain.enums.InvoiceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Validates invoice domain rules before persisting. */
@Service
public class InvoiceValidationService {

    private final ObjectMapper objectMapper;

    public InvoiceValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates invoice header and line-level rules.
     *
     * @param invoice the invoice to validate
     * @return list of errors (empty if valid)
     */
    public List<ValidationError> validate(Invoice invoice) {
        List<ValidationError> errors = new ArrayList<>();

        validateHeader(invoice, errors);
        validateLines(invoice, errors);

        return errors;
    }

    private void validateHeader(Invoice invoice, List<ValidationError> errors) {
        if (invoice.getIssueDate() != null
                && invoice.getIssueDate().isAfter(LocalDate.now())) {
            errors.add(new ValidationError("issueDate",
                    "Issue date cannot be in the future"));
        }

        if (invoice.getSupplyDate() != null && invoice.getSupplyEndDate() != null
                && !invoice.getSupplyEndDate().isAfter(invoice.getSupplyDate())) {
            errors.add(new ValidationError("supplyEndDate",
                    "Supply end date must be after supply date"));
        }

        if (invoice.getType() == InvoiceType.CREDIT_NOTE
                || invoice.getType() == InvoiceType.DEBIT_NOTE) {
            if (invoice.getOriginalInvoice() == null) {
                errors.add(new ValidationError("originalInvoiceId",
                        "Original invoice reference is required for "
                                + invoice.getType().name()));
            }
        }

        validateSubtypeFlags(invoice, errors);
    }

    private void validateSubtypeFlags(Invoice invoice,
            List<ValidationError> errors) {
        if (invoice.getSubtypeFlags() == null
                || invoice.getSubtypeFlags().isBlank()
                || "{}".equals(invoice.getSubtypeFlags())) {
            return;
        }
        try {
            JsonNode flags = objectMapper.readTree(invoice.getSubtypeFlags());
            boolean selfBilled = flags.path("selfBilled").asBoolean(false);
            boolean export = flags.path("export").asBoolean(false);
            if (selfBilled && export) {
                errors.add(new ValidationError("subtypeFlags",
                        "self_billed + export combination is invalid"));
            }
        } catch (Exception e) {
            errors.add(new ValidationError("subtypeFlags",
                    "Invalid JSON format for subtype flags"));
        }
    }

    private void validateLines(Invoice invoice, List<ValidationError> errors) {
        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            errors.add(new ValidationError("lines",
                    "Invoice must have at least one line item"));
            return;
        }

        if (invoice.getBuyer() != null
                && invoice.getBuyer().getCustomerType() == CustomerType.B2B) {
            if (invoice.getBuyer().getVatNumber() == null
                    || invoice.getBuyer().getVatNumber().isBlank()) {
                errors.add(new ValidationError("buyerId",
                        "Buyer VAT number is required for B2B invoices"));
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            InvoiceLine line = lines.get(i);
            String prefix = "lines[" + i + "].";

            if (line.getQuantity() != null
                    && line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new ValidationError(prefix + "quantity",
                        "Quantity must be positive"));
            }
            if (line.getUnitPrice() != null
                    && line.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new ValidationError(prefix + "unitPrice",
                        "Unit price must be positive"));
            }
            if (line.getDiscountAmount() != null
                    && line.getDiscountAmount().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new ValidationError(prefix + "discountAmount",
                        "Discount amount cannot be negative"));
            }
            if (line.getDescriptionEn() == null
                    || line.getDescriptionEn().isBlank()) {
                errors.add(new ValidationError(prefix + "descriptionEn",
                        "Line description is required"));
            }
        }
    }

    /** A single validation error with field reference and message. */
    public static class ValidationError {

        private final String field;
        private final String message;

        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
