package com.einvoice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.InvoiceVatBreakdown;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvoiceCalculationServiceTest {

    private InvoiceCalculationService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceCalculationService();
    }

    private Invoice createInvoice(List<InvoiceLine> lines) {
        Invoice invoice = Invoice.builder().build();
        invoice.setLines(lines);
        invoice.setVatBreakdown(new ArrayList<>());
        invoice.setPrepaidAmount(BigDecimal.ZERO);
        invoice.setTotalAllowances(BigDecimal.ZERO);
        return invoice;
    }

    private InvoiceLine createLine(String vatCategory, BigDecimal vatRate,
            BigDecimal unitPrice, BigDecimal quantity, BigDecimal discount) {
        return InvoiceLine.builder()
                .vatCategory(vatCategory)
                .vatRate(vatRate)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .discountAmount(discount != null ? discount : BigDecimal.ZERO)
                .build();
    }

    @Test
    void recalculate_singleLine_standardVat15() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("2.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("200.00"), invoice.getTotalLineNet());
        assertEquals(new BigDecimal("200.00"), invoice.getTotalWithoutVat());
        assertEquals(new BigDecimal("30.00"), invoice.getTotalVat());
        assertEquals(new BigDecimal("230.00"), invoice.getTotalWithVat());
        assertEquals(new BigDecimal("230.00"), invoice.getAmountDue());

        assertEquals(new BigDecimal("200.00"), line.getLineNetAmount());
        assertEquals(new BigDecimal("30.00"), line.getLineVatAmount());
        assertEquals(new BigDecimal("230.00"), line.getLineTotal());
    }

    @Test
    void recalculate_singleLine_withDiscount() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("3.0000"),
                new BigDecimal("50.00"));
        Invoice invoice = createInvoice(List.of(line));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("250.00"), invoice.getTotalLineNet());
        assertEquals(new BigDecimal("250.00"), invoice.getTotalWithoutVat());
        assertEquals(new BigDecimal("37.50"), invoice.getTotalVat());
        assertEquals(new BigDecimal("287.50"), invoice.getTotalWithVat());
    }

    @Test
    void recalculate_multipleLines_aggregatesCorrectly() {
        InvoiceLine line1 = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        InvoiceLine line2 = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("200.0000"), new BigDecimal("2.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line1, line2));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("500.00"), invoice.getTotalLineNet());
        assertEquals(new BigDecimal("75.00"), invoice.getTotalVat());
        assertEquals(new BigDecimal("575.00"), invoice.getTotalWithVat());
    }

    @Test
    void recalculate_zeroRatedVat() {
        InvoiceLine line = createLine("Z", new BigDecimal("0.00"),
                new BigDecimal("100.0000"), new BigDecimal("5.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("500.00"), invoice.getTotalLineNet());
        assertEquals(new BigDecimal("0.00"), invoice.getTotalVat());
        assertEquals(new BigDecimal("500.00"), invoice.getTotalWithVat());
    }

    @Test
    void recalculate_exemptVat() {
        InvoiceLine line = createLine("E", new BigDecimal("0.00"),
                new BigDecimal("50.0000"), new BigDecimal("3.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("150.00"), invoice.getTotalLineNet());
        assertEquals(new BigDecimal("0.00"), invoice.getTotalVat());
    }

    @Test
    void recalculate_mixedVatCategories_createsBreakdown() {
        InvoiceLine line1 = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        InvoiceLine line2 = createLine("Z", new BigDecimal("0.00"),
                new BigDecimal("200.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line1, line2));

        service.recalculate(invoice);

        assertEquals(2, invoice.getVatBreakdown().size());

        InvoiceVatBreakdown standardBd = invoice.getVatBreakdown().stream()
                .filter(bd -> "S".equals(bd.getVatCategoryCode()))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("100.00"), standardBd.getTaxableAmount());
        assertEquals(new BigDecimal("15.00"), standardBd.getTaxAmount());

        InvoiceVatBreakdown zeroBd = invoice.getVatBreakdown().stream()
                .filter(bd -> "Z".equals(bd.getVatCategoryCode()))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("200.00"), zeroBd.getTaxableAmount());
        assertEquals(new BigDecimal("0.00"), zeroBd.getTaxAmount());
    }

    @Test
    void recalculate_sameVatCategoryAndRate_aggregatesIntoSingleBreakdown() {
        InvoiceLine line1 = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        InvoiceLine line2 = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("200.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line1, line2));

        service.recalculate(invoice);

        assertEquals(1, invoice.getVatBreakdown().size());
        assertEquals(new BigDecimal("300.00"),
                invoice.getVatBreakdown().get(0).getTaxableAmount());
        assertEquals(new BigDecimal("45.00"),
                invoice.getVatBreakdown().get(0).getTaxAmount());
    }

    @Test
    void recalculate_withPrepaidAmount_deductsFromTotal() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));
        invoice.setPrepaidAmount(new BigDecimal("50.00"));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("115.00"), invoice.getTotalWithVat());
        assertEquals(new BigDecimal("65.00"), invoice.getAmountDue());
    }

    @Test
    void recalculate_withAllowances_subtractsFromLineNet() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));
        invoice.setTotalAllowances(new BigDecimal("10.00"));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("100.00"), invoice.getTotalLineNet());
        assertEquals(new BigDecimal("90.00"), invoice.getTotalWithoutVat());
        assertEquals(new BigDecimal("13.50"), invoice.getTotalVat());
        assertEquals(new BigDecimal("103.50"), invoice.getTotalWithVat());
    }

    @Test
    void recalculate_emptyLines_setsAllToZero() {
        Invoice invoice = createInvoice(List.of());

        service.recalculate(invoice);

        assertEquals(BigDecimal.ZERO, invoice.getTotalLineNet());
        assertEquals(BigDecimal.ZERO, invoice.getTotalWithoutVat());
        assertEquals(BigDecimal.ZERO, invoice.getTotalVat());
        assertEquals(BigDecimal.ZERO, invoice.getTotalWithVat());
        assertEquals(BigDecimal.ZERO, invoice.getAmountDue());
        assertTrue(invoice.getVatBreakdown().isEmpty());
    }

    @Test
    void recalculate_nullLines_setsAllToZero() {
        Invoice invoice = createInvoice(null);

        service.recalculate(invoice);

        assertEquals(BigDecimal.ZERO, invoice.getTotalLineNet());
        assertEquals(BigDecimal.ZERO, invoice.getTotalWithoutVat());
        assertEquals(BigDecimal.ZERO, invoice.getTotalVat());
        assertEquals(BigDecimal.ZERO, invoice.getTotalWithVat());
        assertEquals(BigDecimal.ZERO, invoice.getAmountDue());
    }

    @Test
    void recalculate_fractionalQuantities_roundsCorrectly() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("33.3333"), new BigDecimal("3.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("100.00"), line.getLineNetAmount());
        assertEquals(new BigDecimal("15.00"), line.getLineVatAmount());
        assertEquals(new BigDecimal("115.00"), line.getLineTotal());
    }

    @Test
    void recalculate_halfUpRounding_edgeCase() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("33.3350"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("33.34"), line.getLineNetAmount());
    }

    @Test
    void recalculate_multipleVatRates_differentCategories() {
        InvoiceLine standardLine = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("200.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        InvoiceLine zeroLine = createLine("Z", new BigDecimal("0.00"),
                new BigDecimal("100.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        InvoiceLine exemptLine = createLine("E", new BigDecimal("0.00"),
                new BigDecimal("50.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(standardLine, zeroLine, exemptLine));

        service.recalculate(invoice);

        assertEquals(new BigDecimal("350.00"), invoice.getTotalLineNet());
        assertEquals(new BigDecimal("30.00"), invoice.getTotalVat());
        assertEquals(new BigDecimal("380.00"), invoice.getTotalWithVat());

        assertEquals(3, invoice.getVatBreakdown().size());
    }

    @Test
    void calculateLine_singleLine_computesCorrectAmounts() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("2.5000"),
                new BigDecimal("10.00"));

        service.calculateLine(line);

        assertEquals(new BigDecimal("240.00"), line.getLineNetAmount());
        assertEquals(new BigDecimal("36.00"), line.getLineVatAmount());
        assertEquals(new BigDecimal("276.00"), line.getLineTotal());
    }

    @Test
    void calculateLine_nullDiscount_treatedAsZero() {
        InvoiceLine line = InvoiceLine.builder()
                .vatCategory("S")
                .vatRate(new BigDecimal("15.00"))
                .unitPrice(new BigDecimal("100.0000"))
                .quantity(new BigDecimal("1.0000"))
                .build();

        service.calculateLine(line);

        assertEquals(new BigDecimal("100.00"), line.getLineNetAmount());
        assertEquals(new BigDecimal("15.00"), line.getLineVatAmount());
    }

    @Test
    void recalculate_nullPrepaid_treatedAsZero() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));
        invoice.setPrepaidAmount(null);

        service.recalculate(invoice);

        assertEquals(new BigDecimal("115.00"), invoice.getTotalWithVat());
        assertEquals(new BigDecimal("115.00"), invoice.getAmountDue());
    }

    @Test
    void recalculate_nullAllowances_treatedAsZero() {
        InvoiceLine line = createLine("S", new BigDecimal("15.00"),
                new BigDecimal("100.0000"), new BigDecimal("1.0000"), BigDecimal.ZERO);
        Invoice invoice = createInvoice(List.of(line));
        invoice.setTotalAllowances(null);

        service.recalculate(invoice);

        assertEquals(new BigDecimal("100.00"), invoice.getTotalWithoutVat());
    }
}
