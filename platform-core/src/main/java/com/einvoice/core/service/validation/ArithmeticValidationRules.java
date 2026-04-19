package com.einvoice.core.service.validation;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.InvoiceVatBreakdown;
import com.einvoice.core.domain.enums.Authority;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Validates arithmetic consistency of invoice calculations. */
@Component
public class ArithmeticValidationRules implements ValidationRule {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Override
    public List<ValidationError> validate(Invoice invoice, Authority authority) {
        List<ValidationError> errors = new ArrayList<>();
        if (invoice.getLines() == null || invoice.getLines().isEmpty()) {
            return errors;
        }
        validateLineNet(invoice, errors);
        validateLineVat(invoice, errors);
        validateDocumentTotals(invoice, errors);
        validateVatBreakdown(invoice, errors);
        validateRounding(invoice, errors);
        return errors;
    }

    private void validateLineNet(Invoice invoice, List<ValidationError> errors) {
        for (InvoiceLine line : invoice.getLines()) {
            BigDecimal expected = line.getUnitPrice()
                    .multiply(line.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);
            if (line.getDiscountAmount() != null) {
                expected = expected.subtract(line.getDiscountAmount());
            }
            if (line.getLineNetAmount() != null && line.getLineNetAmount().compareTo(expected) != 0) {
                errors.add(new ValidationError(
                        ValidationLayer.ARITHMETIC, null, "ARITH-001",
                        "lineNetAmount",
                        "Line net amount mismatch for line " + line.getSortOrder()
                                + ": expected " + expected + ", got " + line.getLineNetAmount(),
                        ValidationSeverity.ERROR));
            }
        }
    }

    private void validateLineVat(Invoice invoice, List<ValidationError> errors) {
        for (InvoiceLine line : invoice.getLines()) {
            if (line.getLineNetAmount() == null || line.getVatRate() == null) {
                continue;
            }
            BigDecimal expected = line.getLineNetAmount()
                    .multiply(line.getVatRate())
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            if (line.getLineVatAmount() != null && line.getLineVatAmount().compareTo(expected) != 0) {
                errors.add(new ValidationError(
                        ValidationLayer.ARITHMETIC, null, "ARITH-002",
                        "lineVatAmount",
                        "Line VAT amount mismatch for line " + line.getSortOrder()
                                + ": expected " + expected + ", got " + line.getLineVatAmount(),
                        ValidationSeverity.ERROR));
            }
        }
    }

    private void validateDocumentTotals(Invoice invoice, List<ValidationError> errors) {
        BigDecimal expectedLineNet = BigDecimal.ZERO;
        BigDecimal expectedTotalVat = BigDecimal.ZERO;
        for (InvoiceLine line : invoice.getLines()) {
            if (line.getLineNetAmount() != null) {
                expectedLineNet = expectedLineNet.add(line.getLineNetAmount());
            }
            if (line.getLineVatAmount() != null) {
                expectedTotalVat = expectedTotalVat.add(line.getLineVatAmount());
            }
        }

        BigDecimal expectedTotalWithoutVat = expectedLineNet;
        if (invoice.getTotalAllowances() != null) {
            expectedTotalWithoutVat = expectedTotalWithoutVat.subtract(invoice.getTotalAllowances());
        }

        if (invoice.getTotalLineNet() != null && invoice.getTotalLineNet().compareTo(expectedLineNet) != 0) {
            errors.add(new ValidationError(
                    ValidationLayer.ARITHMETIC, null, "ARITH-003",
                    "totalLineNet", "Total line net mismatch", ValidationSeverity.ERROR));
        }

        if (invoice.getTotalWithoutVat() != null
                && invoice.getTotalWithoutVat().compareTo(
                        expectedTotalWithoutVat.setScale(
                                2, RoundingMode.HALF_UP)) != 0) {
            errors.add(new ValidationError(
                    ValidationLayer.ARITHMETIC, null, "ARITH-003",
                    "totalWithoutVat", "Total without VAT mismatch", ValidationSeverity.ERROR));
        }

        if (invoice.getTotalVat() != null && invoice.getTotalVat().compareTo(expectedTotalVat) != 0) {
            errors.add(new ValidationError(
                    ValidationLayer.ARITHMETIC, null, "ARITH-003",
                    "totalVat", "Total VAT mismatch", ValidationSeverity.ERROR));
        }

        BigDecimal expectedTotalWithVat = expectedTotalWithoutVat.add(expectedTotalVat);
        if (invoice.getPrepaidAmount() != null) {
            expectedTotalWithVat = expectedTotalWithVat.subtract(invoice.getPrepaidAmount());
        }
        if (invoice.getTotalWithVat() != null
                && invoice.getTotalWithVat().compareTo(expectedTotalWithVat.setScale(2, RoundingMode.HALF_UP)) != 0) {
            errors.add(new ValidationError(
                    ValidationLayer.ARITHMETIC, null, "ARITH-003",
                    "totalWithVat", "Total with VAT mismatch", ValidationSeverity.ERROR));
        }
    }

    private void validateVatBreakdown(Invoice invoice, List<ValidationError> errors) {
        if (invoice.getVatBreakdown() == null || invoice.getVatBreakdown().isEmpty()) {
            return;
        }
        java.util.Map<String, BigDecimal> lineVatByCategory = new java.util.LinkedHashMap<>();
        java.util.Map<String, BigDecimal> lineTaxableByCategory = new java.util.LinkedHashMap<>();
        for (InvoiceLine line : invoice.getLines()) {
            String cat = line.getVatCategory();
            if (cat == null) {
                continue;
            }
            BigDecimal taxable = line.getLineNetAmount() != null
                    ? line.getLineNetAmount() : BigDecimal.ZERO;
            BigDecimal vat = line.getLineVatAmount() != null
                    ? line.getLineVatAmount() : BigDecimal.ZERO;
            lineTaxableByCategory.merge(cat, taxable, BigDecimal::add);
            lineVatByCategory.merge(cat, vat, BigDecimal::add);
        }

        for (InvoiceVatBreakdown bd : invoice.getVatBreakdown()) {
            String key = bd.getVatCategoryCode();
            BigDecimal lineTax = lineVatByCategory.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal lineTaxable = lineTaxableByCategory.getOrDefault(key, BigDecimal.ZERO);
            if (bd.getTaxAmount() != null && bd.getTaxAmount().compareTo(
                    lineTax.setScale(2, RoundingMode.HALF_UP)) != 0) {
                errors.add(new ValidationError(
                        ValidationLayer.ARITHMETIC, null, "ARITH-004",
                        "vatBreakdown", "VAT breakdown tax amount mismatch for category " + key,
                        ValidationSeverity.ERROR));
            }
            if (bd.getTaxableAmount() != null
                    && bd.getTaxableAmount().compareTo(
                            lineTaxable.setScale(
                                    2, RoundingMode.HALF_UP)) != 0) {
                errors.add(new ValidationError(
                        ValidationLayer.ARITHMETIC, null, "ARITH-004",
                        "vatBreakdown",
                        "VAT breakdown taxable amount mismatch"
                                + " for category " + key,
                        ValidationSeverity.ERROR));
            }
        }
    }

    private void validateRounding(Invoice invoice, List<ValidationError> errors) {
        for (InvoiceLine line : invoice.getLines()) {
            if (line.getLineNetAmount() != null && line.getLineNetAmount().scale() > 2) {
                errors.add(new ValidationError(
                        ValidationLayer.ARITHMETIC, null, "ARITH-005",
                        "lineNetAmount",
                        "Line net amount for line " + line.getSortOrder() + " exceeds 2 decimal places",
                        ValidationSeverity.ERROR));
            }
            if (line.getLineVatAmount() != null && line.getLineVatAmount().scale() > 2) {
                errors.add(new ValidationError(
                        ValidationLayer.ARITHMETIC, null, "ARITH-005",
                        "lineVatAmount",
                        "Line VAT amount for line " + line.getSortOrder() + " exceeds 2 decimal places",
                        ValidationSeverity.ERROR));
            }
        }
    }
}
