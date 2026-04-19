package com.einvoice.core.service;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.service.validation.ValidationLayer;
import com.einvoice.core.service.validation.ValidationSeverity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Validates invoice domain rules before persisting a draft.
 *
 * <p>Delegates to the three-layer {@link ValidationService} but restricts
 * evaluation to {@link ValidationLayer#STRUCTURAL STRUCTURAL} and
 * {@link ValidationLayer#ARITHMETIC ARITHMETIC} layers only — COMPLIANCE and
 * READINESS checks are submission-time concerns and must not block draft
 * creation.
 *
 * <p>Additionally enforces three draft-scope rules that are not covered by
 * any {@code ValidationRule} implementation:
 * <ul>
 *   <li>Issue date must not be in the future</li>
 *   <li>Supply end date must be after supply date</li>
 *   <li>selfBilled + export subtype flag combination is invalid</li>
 * </ul>
 */
@Service
public class InvoiceValidationService {

    private final ValidationService validationService;
    private final ObjectMapper objectMapper;

    public InvoiceValidationService(ValidationService validationService,
            ObjectMapper objectMapper) {
        this.validationService = validationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates invoice header and line-level rules for draft persistence.
     *
     * @param invoice the invoice to validate
     * @return list of errors (empty if valid)
     */
    public List<ValidationError> validate(Invoice invoice) {
        Authority authority = invoice.getAuthority() != null
                ? invoice.getAuthority() : Authority.ZATCA;

        ValidationService.ValidationResult result =
                validationService.validate(invoice, authority);

        List<ValidationError> legacyErrors = new ArrayList<>();

        for (com.einvoice.core.service.validation.ValidationError ve : result.errors()) {
            if (ve.layer() == ValidationLayer.STRUCTURAL
                    || ve.layer() == ValidationLayer.ARITHMETIC) {
                legacyErrors.add(new ValidationError(ve.field(), ve.message()));
            }
        }

        validateFutureIssueDate(invoice, legacyErrors);
        validateSupplyDateOrdering(invoice, legacyErrors);
        validateSubtypeFlags(invoice, legacyErrors);

        return legacyErrors;
    }

    private void validateFutureIssueDate(Invoice invoice,
            List<ValidationError> errors) {
        if (invoice.getIssueDate() != null
                && invoice.getIssueDate().isAfter(LocalDate.now())) {
            errors.add(new ValidationError("issueDate",
                    "Issue date cannot be in the future"));
        }
    }

    private void validateSupplyDateOrdering(Invoice invoice,
            List<ValidationError> errors) {
        if (invoice.getSupplyDate() != null && invoice.getSupplyEndDate() != null
                && !invoice.getSupplyEndDate().isAfter(invoice.getSupplyDate())) {
            errors.add(new ValidationError("supplyEndDate",
                    "Supply end date must be after supply date"));
        }
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
