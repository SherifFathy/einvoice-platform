package com.einvoice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.InvoiceVatBreakdown;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.service.validation.ArithmeticValidationRules;
import com.einvoice.core.service.validation.StructuralValidationRules;
import com.einvoice.core.service.validation.SubmissionReadinessRules;
import com.einvoice.core.service.validation.ValidationError;
import com.einvoice.core.service.validation.ValidationLayer;
import com.einvoice.core.service.validation.ValidationSeverity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private AuthorityConfigRepository authorityConfigRepository;

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        StructuralValidationRules structuralRules = new StructuralValidationRules();
        ArithmeticValidationRules arithmeticRules = new ArithmeticValidationRules();
        SubmissionReadinessRules readinessRules = new SubmissionReadinessRules(authorityConfigRepository);
        validationService = new ValidationService(List.of(structuralRules, arithmeticRules, readinessRules));
    }

    private Invoice buildValidInvoice() {
        Company company = Company.builder().id(1L).build();
        Branch branch = Branch.builder().id(1L).company(company).build();
        User user = User.builder().id(1L).build();

        InvoiceLine line = InvoiceLine.builder()
                .descriptionEn("Test item")
                .quantity(new BigDecimal("2"))
                .unit("EA")
                .unitPrice(new BigDecimal("100.00"))
                .discountAmount(BigDecimal.ZERO)
                .vatCategory("S")
                .vatRate(new BigDecimal("15.00"))
                .lineNetAmount(new BigDecimal("200.00"))
                .lineVatAmount(new BigDecimal("30.00"))
                .lineTotal(new BigDecimal("230.00"))
                .sortOrder(1)
                .build();

        InvoiceVatBreakdown vatBd = InvoiceVatBreakdown.builder()
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .taxableAmount(new BigDecimal("200.00"))
                .taxAmount(new BigDecimal("30.00"))
                .build();

        Invoice invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .company(company)
                .branch(branch)
                .type(InvoiceType.TAX_INVOICE)
                .issueDate(LocalDate.now())
                .currency("SAR")
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .totalLineNet(new BigDecimal("200.00"))
                .totalAllowances(BigDecimal.ZERO)
                .totalWithoutVat(new BigDecimal("200.00"))
                .totalVat(new BigDecimal("30.00"))
                .totalWithVat(new BigDecimal("230.00"))
                .amountDue(new BigDecimal("230.00"))
                .prepaidAmount(BigDecimal.ZERO)
                .createdBy(user)
                .lines(List.of(line))
                .vatBreakdown(List.of(vatBd))
                .build();
        line.setInvoice(invoice);
        vatBd.setInvoice(invoice);

        AuthorityConfig config = AuthorityConfig.builder()
                .id(1L)
                .branch(branch)
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .isActive(true)
                .csidEncrypted(new byte[]{1})
                .invoiceCounter(1L)
                .build();
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                .thenReturn(Optional.of(config));

        return invoice;
    }

    @Nested
    class StructuralRules {

        @Test
        void struct001_missingIssueDate() {
            Invoice invoice = buildValidInvoice();
            invoice.setIssueDate(null);
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-001") && e.field().equals("issueDate")));
        }

        @Test
        void struct001_missingCurrency() {
            Invoice invoice = buildValidInvoice();
            invoice.setCurrency("");
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-001") && e.field().equals("currency")));
        }

        @Test
        void struct001_missingBranch() {
            Invoice invoice = buildValidInvoice();
            invoice.setBranch(null);
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-001") && e.field().equals("branch")));
        }

        @Test
        void struct001_missingCreatedBy() {
            Invoice invoice = buildValidInvoice();
            invoice.setCreatedBy(null);
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-001") && e.field().equals("createdBy")));
        }

        @Test
        void struct002_noLines() {
            Invoice invoice = buildValidInvoice();
            invoice.setLines(List.of());
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-002")));
        }

        @Test
        void struct003_b2bMissingVatNumber() {
            Invoice invoice = buildValidInvoice();
            Customer buyer = Customer.builder().id(1L).customerType(CustomerType.B2B).build();
            invoice.setBuyer(buyer);
            invoice.setBuyerData("{}");
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-003")));
        }

        @Test
        void struct003_b2bWithVatNumber_passes() {
            Invoice invoice = buildValidInvoice();
            Customer buyer = Customer.builder().id(1L).customerType(CustomerType.B2B).build();
            invoice.setBuyer(buyer);
            invoice.setBuyerData("{\"vatNumber\":\"300000123456789\"}");
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertFalse(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-003")));
        }

        @Test
        void struct004_creditNoteWithoutReference() {
            Invoice invoice = buildValidInvoice();
            invoice.setType(InvoiceType.CREDIT_NOTE);
            invoice.setOriginalInvoice(null);
            invoice.setExternalInvoiceReference(null);
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-004")));
        }

        @Test
        void struct004_creditNoteWithExternalReference_passes() {
            Invoice invoice = buildValidInvoice();
            invoice.setType(InvoiceType.CREDIT_NOTE);
            invoice.setExternalInvoiceReference("EXT-123");
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertFalse(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("STRUCT-004")));
        }

        @Test
        void validInvoice_noStructuralErrors() {
            Invoice invoice = buildValidInvoice();
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertFalse(result.errors().stream()
                    .anyMatch(e -> e.layer() == ValidationLayer.STRUCTURAL));
        }
    }

    @Nested
    class ArithmeticRules {

        @Test
        void arith001_lineNetMismatch() {
            Invoice invoice = buildValidInvoice();
            invoice.getLines().get(0).setLineNetAmount(new BigDecimal("999.99"));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-001")));
        }

        @Test
        void arith001_lineNetCorrect_passes() {
            Invoice invoice = buildValidInvoice();
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertFalse(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-001")));
        }

        @Test
        void arith002_lineVatMismatch() {
            Invoice invoice = buildValidInvoice();
            invoice.getLines().get(0).setLineVatAmount(new BigDecimal("99.99"));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-002")));
        }

        @Test
        void arith002_lineVatCorrect_passes() {
            Invoice invoice = buildValidInvoice();
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertFalse(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-002")));
        }

        @Test
        void arith003_totalLineNetMismatch() {
            Invoice invoice = buildValidInvoice();
            invoice.setTotalLineNet(new BigDecimal("999.99"));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-003") && e.field().equals("totalLineNet")));
        }

        @Test
        void arith003_totalVatMismatch() {
            Invoice invoice = buildValidInvoice();
            invoice.setTotalVat(new BigDecimal("99.99"));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-003") && e.field().equals("totalVat")));
        }

        @Test
        void arith003_totalWithVatMismatch() {
            Invoice invoice = buildValidInvoice();
            invoice.setTotalWithVat(new BigDecimal("999.99"));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-003") && e.field().equals("totalWithVat")));
        }

        @Test
        void arith004_vatBreakdownMismatch() {
            Invoice invoice = buildValidInvoice();
            invoice.getVatBreakdown().get(0).setTaxAmount(new BigDecimal("99.99"));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-004")));
        }

        @Test
        void arith005_excessiveDecimals() {
            Invoice invoice = buildValidInvoice();
            invoice.getLines().get(0).setLineNetAmount(new BigDecimal("200.001"));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("ARITH-005")));
        }

        @Test
        void validInvoice_noArithmeticErrors() {
            Invoice invoice = buildValidInvoice();
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertFalse(result.errors().stream()
                    .anyMatch(e -> e.layer() == ValidationLayer.ARITHMETIC));
        }
    }

    @Nested
    class ReadinessRules {

        @Test
        void ready001_configNotFound() {
            Invoice invoice = buildValidInvoice();
            when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                    1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                    .thenReturn(Optional.empty());
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("READY-001")));
        }

        @Test
        void ready001_configNotActive() {
            Invoice invoice = buildValidInvoice();
            AuthorityConfig config = AuthorityConfig.builder()
                    .id(1L).isActive(false).build();
            when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                    1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                    .thenReturn(Optional.of(config));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("READY-001")));
        }

        @Test
        void ready002_noCredentials() {
            Invoice invoice = buildValidInvoice();
            AuthorityConfig config = AuthorityConfig.builder()
                    .id(1L).isActive(true)
                    .csidEncrypted(null)
                    .tokenDataEncrypted(null)
                    .certificateExpiryDate(OffsetDateTime.now().plusDays(30))
                    .invoiceCounter(1L)
                    .build();
            when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                    1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                    .thenReturn(Optional.of(config));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("READY-002")));
        }

        @Test
        void ready002_expiredCertificate() {
            Invoice invoice = buildValidInvoice();
            AuthorityConfig config = AuthorityConfig.builder()
                    .id(1L).isActive(true)
                    .csidEncrypted(new byte[]{1})
                    .certificateExpiryDate(OffsetDateTime.now().minusDays(1))
                    .invoiceCounter(1L)
                    .build();
            when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                    1L, Authority.ZATCA, Environment.ZATCA_SANDBOX))
                    .thenReturn(Optional.of(config));
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("READY-002")));
        }

        @Test
        void ready003_noInvoiceNumber() {
            Invoice invoice = buildValidInvoice();
            invoice.setInvoiceNumber(null);
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertTrue(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("READY-003")));
        }

        @Test
        void ready003_withInvoiceNumber_passes() {
            Invoice invoice = buildValidInvoice();
            invoice.setInvoiceNumber("INV-001");
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertFalse(result.errors().stream()
                    .anyMatch(e -> e.ruleId().equals("READY-003")));
        }

        @Test
        void validInvoice_noReadinessErrors() {
            Invoice invoice = buildValidInvoice();
            invoice.setInvoiceNumber("INV-001");
            ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
            assertFalse(result.errors().stream()
                    .anyMatch(e -> e.layer() == ValidationLayer.READINESS));
        }
    }

    @Test
    void fullyValidInvoice_passes() {
        Invoice invoice = buildValidInvoice();
        invoice.setInvoiceNumber("INV-001");
        ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
        assertFalse(result.hasErrors());
    }

    @Test
    void validationResult_groupsByLayer() {
        Invoice invoice = buildValidInvoice();
        invoice.setIssueDate(null);
        invoice.setLines(List.of());
        ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
        assertFalse(result.errorsByLayer().get(ValidationLayer.STRUCTURAL).isEmpty());
    }

    @Test
    void validationResult_separatesErrorsAndWarnings() {
        Invoice invoice = buildValidInvoice();
        ValidationService.ValidationResult result = validationService.validate(invoice, Authority.ZATCA);
        assertEquals(0, result.warnings().size());
    }
}
