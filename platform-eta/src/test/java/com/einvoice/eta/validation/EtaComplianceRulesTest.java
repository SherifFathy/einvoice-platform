package com.einvoice.eta.validation;

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
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.service.validation.ValidationError;
import com.einvoice.core.service.validation.ValidationLayer;
import com.einvoice.core.service.validation.ValidationSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class EtaComplianceRulesTest {

    @Mock
    private AuthorityConfigRepository authorityConfigRepository;

    private EtaComplianceRules rules;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        rules = new EtaComplianceRules(authorityConfigRepository, objectMapper);
    }

    private Invoice buildValidEtaInvoice() {
        Company company = Company.builder().id(1L).build();
        Branch branch = Branch.builder().id(1L).company(company).build();
        Customer buyer = Customer.builder().id(1L).build();

        InvoiceLine line = InvoiceLine.builder()
                .descriptionEn("Test item")
                .quantity(BigDecimal.ONE)
                .unit("EA")
                .unitPrice(new BigDecimal("100.00"))
                .discountAmount(BigDecimal.ZERO)
                .vatCategory("S")
                .vatRate(new BigDecimal("14.00"))
                .lineNetAmount(new BigDecimal("100.00"))
                .lineVatAmount(new BigDecimal("14.00"))
                .lineTotal(new BigDecimal("114.00"))
                .sortOrder(1)
                .build();

        return Invoice.builder()
                .id(UUID.randomUUID())
                .company(company)
                .branch(branch)
                .type(InvoiceType.TAX_INVOICE)
                .issueDate(LocalDate.now())
                .currency("EGP")
                .authority(Authority.ETA)
                .environment(Environment.ETA_PREPRODUCTION)
                .totalWithVat(new BigDecimal("114.00"))
                .totalVat(new BigDecimal("14.00"))
                .buyer(buyer)
                .buyerData("{\"name\":\"Test Buyer\"}")
                .lines(List.of(line))
                .build();
    }

    private void stubDefaultEtaConfig() {
        AuthorityConfig config = AuthorityConfig.builder()
                .id(1L)
                .authority(Authority.ETA)
                .environment(Environment.ETA_PREPRODUCTION)
                .enabledDocumentTypes("[\"I\",\"S\",\"C\",\"D\"]")
                .build();
        when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                1L, Authority.ETA, Environment.ETA_PREPRODUCTION))
                .thenReturn(Optional.of(config));
    }

    @Nested
    class Eta001DocumentTypeSchema {

        @Test
        void missingCompany_fails() {
            Invoice invoice = buildValidEtaInvoice();
            invoice.setCompany(null);
            List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-001") && e.field().equals("company")));
        }

        @Test
        void missingTotals_fails() {
            Invoice invoice = buildValidEtaInvoice();
            stubDefaultEtaConfig();
            invoice.setTotalWithVat(null);
            invoice.setTotalVat(null);
            List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-001") && e.field().equals("totalWithVat")));
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-001") && e.field().equals("totalVat")));
        }

        @Test
        void taxInvoiceWithoutBuyer_generatesWarning() {
            Invoice invoice = buildValidEtaInvoice();
            stubDefaultEtaConfig();
            invoice.setBuyer(null);
            invoice.setBuyerData(null);
            invoice.setType(InvoiceType.TAX_INVOICE);
            List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-001")
                            && e.severity() == ValidationSeverity.WARNING
                            && e.field().equals("buyer")));
        }

        @Test
        void completeInvoice_passesEta001() {
            Invoice invoice = buildValidEtaInvoice();
            stubDefaultEtaConfig();
            List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-001")));
        }
    }

    @Nested
    class Eta002EnabledDocumentType {

        @Test
        void enabledType_passes() {
            Invoice invoice = buildValidEtaInvoice();
            stubDefaultEtaConfig();
            invoice.setType(InvoiceType.TAX_INVOICE);
            List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-002")));
        }

        @Test
        void disabledType_fails() {
            Invoice invoice = buildValidEtaInvoice();
            invoice.setType(InvoiceType.TAX_INVOICE);
            AuthorityConfig config = AuthorityConfig.builder()
                    .id(1L)
                    .branch(invoice.getBranch())
                    .authority(Authority.ETA)
                    .environment(Environment.ETA_PREPRODUCTION)
                    .enabledDocumentTypes("[\"S\"]")
                    .build();
            when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                    1L, Authority.ETA, Environment.ETA_PREPRODUCTION))
                    .thenReturn(Optional.of(config));
            List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-002")));
        }

        @Test
        void emptyEnabledTypes_passes() {
            Invoice invoice = buildValidEtaInvoice();
            AuthorityConfig config = AuthorityConfig.builder()
                    .id(1L)
                    .branch(invoice.getBranch())
                    .authority(Authority.ETA)
                    .environment(Environment.ETA_PREPRODUCTION)
                    .enabledDocumentTypes("[]")
                    .build();
            when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                    1L, Authority.ETA, Environment.ETA_PREPRODUCTION))
                    .thenReturn(Optional.of(config));
            List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
            assertFalse(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-002")));
        }

        @Test
        void noConfig_fails() {
            Invoice invoice = buildValidEtaInvoice();
            when(authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                    1L, Authority.ETA, Environment.ETA_PREPRODUCTION))
                    .thenReturn(Optional.empty());
            List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
            assertTrue(errors.stream()
                    .anyMatch(e -> e.ruleId().equals("ETA-002")));
        }
    }

    @Test
    void nonEtaAuthority_returnsEmpty() {
        Invoice invoice = buildValidEtaInvoice();
        List<ValidationError> errors = rules.validate(invoice, Authority.ZATCA);
        assertTrue(errors.isEmpty());
    }

    @Test
    void allErrors_reportComplianceLayer() {
        Invoice invoice = buildValidEtaInvoice();
        stubDefaultEtaConfig();
        invoice.setCompany(null);
        List<ValidationError> errors = rules.validate(invoice, Authority.ETA);
        for (ValidationError e : errors) {
            assertEquals(ValidationLayer.COMPLIANCE, e.layer());
            assertEquals(Authority.ETA, e.authority());
        }
    }
}
