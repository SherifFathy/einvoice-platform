package com.einvoice.zatca.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.service.validation.ValidationError;
import com.einvoice.core.service.validation.ValidationLayer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mapping between nested test groups and rule ids in ZatcaComplianceRules:
 *   BR-KSA-04 (issue date)    -> ZATCA-001
 *   BR-KSA-15 (supply dates)  -> ZATCA-002
 *   BR-KSA-07 (subtype flags) -> ZATCA-003
 *   BR-KSA-09 (seller addr)   -> ZATCA-004
 *   BR-KSA-46 (buyer VAT)     -> ZATCA-005
 *   BR-KSA-56 (credit note)   -> ZATCA-006
 */
class ZatcaComplianceRulesTest {

    private ZatcaComplianceRules rules;

    @BeforeEach
    void setUp() {
        rules = new ZatcaComplianceRules();
    }

    private Invoice buildValidZatcaInvoice() {
        Company company = Company.builder().id(1L).build();
        Branch branch = Branch.builder().id(1L).company(company)
                .street("King Fahd Road").city("Riyadh").build();

        InvoiceLine line = InvoiceLine.builder()
                .descriptionEn("Test item")
                .quantity(BigDecimal.ONE)
                .unit("EA")
                .unitPrice(new BigDecimal("100.00"))
                .discountAmount(BigDecimal.ZERO)
                .vatCategory("S")
                .vatRate(new BigDecimal("15.00"))
                .lineNetAmount(new BigDecimal("100.00"))
                .lineVatAmount(new BigDecimal("15.00"))
                .lineTotal(new BigDecimal("115.00"))
                .sortOrder(1)
                .build();

        return Invoice.builder()
                .id(UUID.randomUUID())
                .company(company)
                .branch(branch)
                .type(InvoiceType.TAX_INVOICE)
                .issueDate(LocalDate.now())
                .supplyDate(LocalDate.now())
                .currency("SAR")
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .subtypeFlags("{}")
                .lines(List.of(line))
                .build();
    }

    @Nested
    class BrKsa04IssueDate {

        @Test
        void futureDate_fails() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setIssueDate(LocalDate.now().plusDays(1));
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-001")));
        }

        @Test
        void today_passes() {
            Invoice invoice = buildValidZatcaInvoice();
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-001")));
        }
    }

    @Nested
    class BrKsa07SubtypeFlags {

        @Test
        void exportAndDomestic_fails() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setSubtypeFlags("{\"exports\":true,\"domestic\":true}");
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-003")));
        }

        @Test
        void exportOnly_passes() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setSubtypeFlags("{\"exports\":true,\"domestic\":false}");
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-003")));
        }

        @Test
        void domesticOnly_passes() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setSubtypeFlags("{\"exports\":false,\"domestic\":true}");
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-003")));
        }
    }

    @Nested
    class BrKsa09SellerAddress {

        @Test
        void missingStreet_fails() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.getBranch().setStreet(null);
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-004") && e.field().equals("branch.street")));
        }

        @Test
        void missingCity_fails() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.getBranch().setCity(null);
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-004") && e.field().equals("branch.city")));
        }

        @Test
        void fullAddress_passes() {
            Invoice invoice = buildValidZatcaInvoice();
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-004")));
        }
    }

    @Nested
    class BrKsa15SupplyDates {

        @Test
        void supplyEndDateBeforeSupplyDate_fails() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setSupplyDate(LocalDate.of(2025, 1, 10));
            invoice.setSupplyEndDate(LocalDate.of(2025, 1, 9));
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-002")));
        }

        @Test
        void supplyEndDateAfterSupplyDate_passes() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setSupplyDate(LocalDate.of(2025, 1, 9));
            invoice.setSupplyEndDate(LocalDate.of(2025, 1, 10));
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-002")));
        }
    }

    @Nested
    class BrKsa46BuyerVatExports {

        @Test
        void exportWithoutBuyerVat_fails() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setSubtypeFlags("{\"exports\":true}");
            Customer buyer = Customer.builder().id(1L).vatNumber(null).build();
            invoice.setBuyer(buyer);
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-005")));
        }

        @Test
        void exportWithBuyerVat_passes() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setSubtypeFlags("{\"exports\":true}");
            Customer buyer = Customer.builder().id(1L).vatNumber("300000123456789").build();
            invoice.setBuyer(buyer);
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-005")));
        }

        @Test
        void domesticNoBuyerVat_passes() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setSubtypeFlags("{\"domestic\":true}");
            Customer buyer = Customer.builder().id(1L).vatNumber(null).build();
            invoice.setBuyer(buyer);
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-005")));
        }
    }

    @Nested
    class BrKsa56CreditNoteRef {

        @Test
        void creditNoteWithoutReference_fails() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setType(InvoiceType.CREDIT_NOTE);
            invoice.setOriginalInvoice(null);
            invoice.setExternalInvoiceReference(null);
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-006")));
        }

        @Test
        void creditNoteWithExternalRef_passes() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setType(InvoiceType.CREDIT_NOTE);
            invoice.setExternalInvoiceReference("EXT-REF-001");
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-006")));
        }

        @Test
        void debitNoteWithoutReference_fails() {
            Invoice invoice = buildValidZatcaInvoice();
            invoice.setType(InvoiceType.DEBIT_NOTE);
            invoice.setOriginalInvoice(null);
            invoice.setExternalInvoiceReference(null);
            List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ZATCA-006")));
        }
    }

    @Test
    void nonZatcaAuthority_returnsEmpty() {
        Invoice invoice = buildValidZatcaInvoice();
        List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
        assertTrue(errors.isEmpty());
    }

    @Test
    void allErrors_reportComplianceLayer() {
        Invoice invoice = buildValidZatcaInvoice();
        invoice.setIssueDate(LocalDate.now().plusDays(1));
        List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
        for (ValidationError e : errors) {
            assertEquals(ValidationLayer.COMPLIANCE, e.layer());
        }
    }
}
