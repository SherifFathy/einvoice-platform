package com.einvoice.zatca.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.zatca.ZatcaStandardAllowance;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardLine;
import com.einvoice.core.domain.zatca.ZatcaStandardTaxSubtotal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Locks in the service-layer guard for {@code §V59.A.1}: even when the
 * database lets two subtotals with the same {@code (header, category, rate)}
 * triple coexist, the UBL serialiser MUST emit at most one
 * {@code cac:TaxSubtotal} block per triple. Pairs with the
 * service-layer uniqueness assertion enforced in
 * {@code ZatcaStandardHeaderService}.
 */
class ZatcaUblBuilderTaxSubtotalDedupTest {

    private ZatcaUblBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ZatcaUblBuilder();
    }

    @Test
    void duplicateSubtotalsDedupedInEmittedUbl() {
        ZatcaStandardHeader header = baseHeader();
        ZatcaStandardTaxSubtotal first = ZatcaStandardTaxSubtotal.builder()
                .header(header)
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .taxableAmount(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("15.00"))
                .build();
        ZatcaStandardTaxSubtotal duplicate = ZatcaStandardTaxSubtotal.builder()
                .header(header)
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .taxableAmount(new BigDecimal("999.00"))
                .taxAmount(new BigDecimal("149.85"))
                .build();
        header.setTaxSubtotals(new ArrayList<>(List.of(first, duplicate)));

        String xml = new String(builder.buildStandardUbl(header),
                StandardCharsets.UTF_8);

        long subtotalCount = xml.split("<cac:TaxSubtotal>").length - 1;
        assertEquals(1, subtotalCount,
                "Duplicate (S,15.00) subtotals must collapse to one"
                        + " <cac:TaxSubtotal> per BR-KSA-EN16931-08");
        assertTrue(xml.contains(
                "<cbc:TaxableAmount>100.00</cbc:TaxableAmount>"),
                "Dedup must keep the first row's taxable amount");
    }

    @Test
    void documentAllowanceBlockEmittedFromChildTable() {
        ZatcaStandardHeader header = baseHeader();
        ZatcaStandardAllowance allowance = ZatcaStandardAllowance.builder()
                .header(header)
                .sequence((short) 1)
                .amount(new BigDecimal("12.50"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .reason("Loyalty discount")
                .build();
        header.setAllowances(new ArrayList<>(List.of(allowance)));

        String xml = new String(builder.buildStandardUbl(header),
                StandardCharsets.UTF_8);

        assertTrue(xml.contains("<cac:AllowanceCharge>"),
                "Document-level allowance must emit <cac:AllowanceCharge>"
                        + " block per BG-20");
        assertTrue(xml.contains("Loyalty discount"),
                "Reason must propagate to UBL output");
        assertTrue(xml.contains(
                "<cbc:AllowanceTotalAmount>12.50</cbc:AllowanceTotalAmount>"),
                "BT-107 must be summed from allowances child table");
    }

    private ZatcaStandardHeader baseHeader() {
        ZatcaStandardHeader header = ZatcaStandardHeader.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000020"))
                .companyId(UUID.fromString(
                        "00000000-0000-0000-0000-000000000021"))
                .authorityEnvironmentId((short) 5)
                .invoiceNumber("STD-DEDUP-001")
                .invoiceTypeCode("388")
                .transactionTypeCode("0100000")
                .issueDate(LocalDate.of(2026, 5, 20))
                .issueTime(LocalTime.of(10, 0, 0))
                .sellerData(Map.of("partyName", "Seller"))
                .sellerVatNumber("300000000000003")
                .sellerCountryCode("SA")
                .buyerData(Map.of("partyName", "Buyer"))
                .buyerVatNumber("300000000100003")
                .buyerCountryCode("SA")
                .currency("SAR")
                .taxCurrency("SAR")
                .lineExtensionAmount(new BigDecimal("100.00"))
                .taxExclusiveAmount(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("15.00"))
                .taxInclusiveAmount(new BigDecimal("115.00"))
                .payableAmount(new BigDecimal("115.00"))
                .prepaidAmount(BigDecimal.ZERO)
                .build();
        header.setLines(new ArrayList<ZatcaStandardLine>());
        return header;
    }
}
