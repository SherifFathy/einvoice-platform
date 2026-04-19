package com.einvoice.core.service;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.service.validation.ValidationError;
import com.einvoice.core.service.validation.ValidationLayer;
import com.einvoice.core.service.validation.ValidationRule;
import com.einvoice.core.service.validation.ValidationSeverity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Orchestrates validation rules grouped by layer. */
@Service
public class ValidationService {

    private final List<ValidationRule> rules;

    public ValidationService(List<ValidationRule> rules) {
        this.rules = rules;
    }

    /**
     * Runs all validation rules and returns grouped results.
     *
     * @param invoice the invoice to validate
     * @param authority the target authority
     * @return the validation result with errors and warnings
     */
    public ValidationResult validate(Invoice invoice, Authority authority) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationError> warnings = new ArrayList<>();

        for (ValidationRule rule : rules) {
            List<ValidationError> results = rule.validate(invoice, authority);
            for (ValidationError ve : results) {
                if (ve.severity() == ValidationSeverity.ERROR) {
                    errors.add(ve);
                } else {
                    warnings.add(ve);
                }
            }
        }

        return new ValidationResult(
                groupByLayer(errors),
                groupByLayer(warnings),
                errors,
                warnings
        );
    }

    private Map<ValidationLayer, List<ValidationError>> groupByLayer(
            List<ValidationError> items) {
        Map<ValidationLayer, List<ValidationError>> grouped =
                new EnumMap<>(ValidationLayer.class);
        for (ValidationLayer layer : ValidationLayer.values()) {
            grouped.put(layer, new ArrayList<>());
        }
        for (ValidationError ve : items) {
            grouped.computeIfAbsent(
                    ve.layer(), k -> new ArrayList<>()).add(ve);
        }
        return grouped;
    }

    /** Result of validation containing errors and warnings grouped by layer. */
    public record ValidationResult(
            Map<ValidationLayer, List<ValidationError>> errorsByLayer,
            Map<ValidationLayer, List<ValidationError>> warningsByLayer,
            List<ValidationError> errors,
            List<ValidationError> warnings
    ) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}
