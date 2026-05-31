package com.einvoice.zatca.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedLine;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedLineAllowance;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardLine;
import com.einvoice.core.domain.zatca.ZatcaStandardLineAllowance;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ZatcaUblBuilderGoldenTest {

    private ZatcaUblBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ZatcaUblBuilder();
    }

    static Stream<Arguments> standardFixtures() {
        return Stream.of(
                Arguments.of(
                        "standard/standard-tax-invoice-0100000.xml",
                        buildStandardHeader(),
                        buildStandardLines()),
                Arguments.of(
                        "standard/standard-credit-note-0100000.xml",
                        buildStandardCreditNoteHeader(),
                        buildStandardLines()));
    }

    static Stream<Arguments> simplifiedFixtures() {
        return Stream.of(
                Arguments.of(
                        "simplified/simplified-standard-0200000.xml",
                        buildSimplifiedHeader()),
                Arguments.of(
                        "simplified/simplified-credit-note-0200000.xml",
                        buildSimplifiedCreditNoteHeader()));
    }

    @ParameterizedTest(name = "standard UBL matches {0}")
    @MethodSource("standardFixtures")
    void standardUblMatchesGolden(String goldenPath,
            ZatcaStandardHeader header,
            List<ZatcaStandardLine> lines) throws IOException {
        header.setLines(lines);
        for (ZatcaStandardLine line : lines) {
            line.setHeader(header);
        }
        byte[] result = builder.buildStandardUbl(header);
        assertNotNull(result);
        maybeWriteGolden(goldenPath, normalize(result));
        byte[] expected = readGolden(goldenPath);
        if (expected.length == 0) {
            return;
        }
        assertArrayEquals(normalize(expected), normalize(result),
                "Standard UBL does not match golden file: " + goldenPath);
    }

    @ParameterizedTest(name = "simplified UBL matches {0}")
    @MethodSource("simplifiedFixtures")
    void simplifiedUblMatchesGolden(String goldenPath,
            ZatcaSimplifiedHeader header) throws IOException {
        header.setLines(buildSimplifiedLines());
        for (ZatcaSimplifiedLine line : header.getLines()) {
            line.setHeader(header);
        }
        byte[] result = builder.buildSimplifiedUbl(header);
        assertNotNull(result);
        maybeWriteGolden(goldenPath, normalize(result));
        byte[] expected = readGolden(goldenPath);
        if (expected.length == 0) {
            return;
        }
        assertArrayEquals(normalize(expected), normalize(result),
                "Simplified UBL does not match golden file: " + goldenPath);
    }

    private byte[] readGolden(String path) throws IOException {
        if ("1".equals(System.getenv("UPDATE_GOLDEN"))) {
            return new byte[0];
        }
        InputStream is = getClass().getResourceAsStream("/golden/" + path);
        if (is == null) {
            throw new IOException("Golden file not found: /golden/" + path);
        }
        return is.readAllBytes();
    }

    private byte[] normalize(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8)
                .trim().getBytes(StandardCharsets.UTF_8);
    }

    private void maybeWriteGolden(String path, byte[] content)
            throws IOException {
        if (!"1".equals(System.getenv("UPDATE_GOLDEN"))) {
            return;
        }
        Path moduleLocal = Path.of("src/test/resources/golden", path);
        Path target = Files.isDirectory(Path.of("src/test/resources"))
                ? moduleLocal
                : Path.of("platform-zatca/src/test/resources/golden",
                        path);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private static Map<String, Object> sellerMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taxRegistrationNumber", "300000000000003");
        m.put("partyName", "Test Seller Co.");
        m.put("addressStreet", "King Fahd Road");
        m.put("addressCityName", "Riyadh");
        m.put("addressPostalZone", "12211");
        m.put("addressCountryCode", "SA");
        return m;
    }

    private static Map<String, Object> buyerBasicMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taxRegistrationNumber", "300000000100003");
        m.put("partyName", "Test Buyer Co.");
        m.put("addressCityName", "Jeddah");
        m.put("addressCountryCode", "SA");
        return m;
    }

    private static Map<String, Object> buyerRichMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taxRegistrationNumber", "300000000100003");
        m.put("partyName", "Test Buyer Co.");
        m.put("addressStreet", "Prince Sultan Road");
        m.put("addressCityName", "Jeddah");
        m.put("addressPostalZone", "23442");
        m.put("addressCountryCode", "SA");
        return m;
    }

    private static ZatcaStandardHeader buildStandardHeader() {
        return ZatcaStandardHeader.builder()
                .id(UUID.fromString(
                        "00000000-0000-0000-0000-000000000001"))
                .companyId(UUID.fromString(
                        "00000000-0000-0000-0000-000000000002"))
                .authorityEnvironmentId((short) 5)
                .invoiceNumber("STD-GOLDEN-001")
                .invoiceTypeCode("388")
                .transactionTypeCode("0100000")
                .issueDate(LocalDate.of(2026, 5, 19))
                .issueTime(LocalTime.of(14, 30, 0))
                .sellerData(sellerMap())
                .sellerVatNumber("300000000000003")
                .sellerPostalCode("12211")
                .sellerCountryCode("SA")
                .buyerData(buyerBasicMap())
                .buyerVatNumber("300000000100003")
                .buyerCountryCode("SA")
                .currency("SAR")
                .taxCurrency("SAR")
                .lineExtensionAmount(new BigDecimal("300.00"))
                .taxExclusiveAmount(new BigDecimal("300.00"))
                .taxInclusiveAmount(new BigDecimal("345.00"))
                .prepaidAmount(BigDecimal.ZERO)
                .payableAmount(new BigDecimal("345.00"))
                .taxAmount(new BigDecimal("45.00"))
                .build();
    }

    private static ZatcaStandardHeader buildStandardCreditNoteHeader() {
        return ZatcaStandardHeader.builder()
                .id(UUID.fromString(
                        "00000000-0000-0000-0000-000000000005"))
                .companyId(UUID.fromString(
                        "00000000-0000-0000-0000-000000000010"))
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("STD-CN-001")
                .invoiceTypeCode("381")
                .transactionTypeCode("0100000")
                .businessProcessCode("reporting:1.0")
                .issuanceReason("Goods returned")
                .billingReferenceId("STD-GOLDEN-001")
                .issueDate(LocalDate.of(2026, 5, 20))
                .issueTime(LocalTime.of(10, 0, 0))
                .sellerData(sellerMap())
                .sellerVatNumber("300000000000003")
                .sellerPostalCode("12211")
                .sellerCountryCode("SA")
                .buyerData(buyerRichMap())
                .buyerVatNumber("300000000100003")
                .buyerPostalCode("23442")
                .buyerCountryCode("SA")
                .currency("SAR")
                .taxCurrency("SAR")
                .paymentMeansCode("30")
                .paymentMeansText("Credit transfer")
                .lineExtensionAmount(new BigDecimal("300.00"))
                .taxExclusiveAmount(new BigDecimal("300.00"))
                .taxInclusiveAmount(new BigDecimal("344.97"))
                .prepaidAmount(BigDecimal.ZERO)
                .roundingAmount(new BigDecimal("-0.03"))
                .payableAmount(new BigDecimal("344.97"))
                .taxAmount(new BigDecimal("45.00"))
                .taxAmountAccountingCurrency(new BigDecimal("45.00"))
                .build();
    }

    private static List<ZatcaStandardLine> buildStandardLines() {
        ZatcaStandardLine line = ZatcaStandardLine.builder()
                .lineNumber(1)
                .itemCode("ITEM-001")
                .description("Service fee")
                .quantity(new BigDecimal("2.00000"))
                .itemNetPrice(new BigDecimal("150.00000"))
                .lineExtensionAmount(new BigDecimal("300.00"))
                .netAmount(new BigDecimal("300.00"))
                .vatAmount(new BigDecimal("15.00"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .build();
        return List.of(line);
    }

    private static ZatcaSimplifiedHeader buildSimplifiedHeader() {
        return ZatcaSimplifiedHeader.builder()
                .id(UUID.fromString(
                        "00000000-0000-0000-0000-000000000002"))
                .companyId(UUID.fromString(
                        "00000000-0000-0000-0000-000000000010"))
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("SIMP-GOLDEN-001")
                .invoiceTypeCode("388")
                .transactionTypeCode("0200000")
                .issueDate(LocalDate.of(2026, 5, 19))
                .issueTime(LocalTime.of(14, 30, 0))
                .sellerData(sellerMap())
                .sellerVatNumber("300000000000003")
                .sellerPostalCode("12211")
                .sellerCountryCode("SA")
                .currency("SAR")
                .taxCurrency("SAR")
                .lineExtensionAmount(new BigDecimal("150.00"))
                .taxExclusiveAmount(new BigDecimal("150.00"))
                .taxInclusiveAmount(new BigDecimal("172.50"))
                .prepaidAmount(BigDecimal.ZERO)
                .payableAmount(new BigDecimal("172.50"))
                .taxAmount(new BigDecimal("22.50"))
                .build();
    }

    private static ZatcaSimplifiedHeader buildSimplifiedCreditNoteHeader() {
        return ZatcaSimplifiedHeader.builder()
                .id(UUID.fromString(
                        "00000000-0000-0000-0000-000000000006"))
                .companyId(UUID.fromString(
                        "00000000-0000-0000-0000-000000000010"))
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("SIMP-CN-001")
                .invoiceTypeCode("381")
                .transactionTypeCode("0200000")
                .issuanceReason("Refund")
                .billingReferenceId("SIMP-GOLDEN-001")
                .paymentMeansCode("10")
                .paymentMeansText("Cash")
                .issueDate(LocalDate.of(2026, 5, 20))
                .issueTime(LocalTime.of(10, 0, 0))
                .sellerData(sellerMap())
                .sellerVatNumber("300000000000003")
                .sellerPostalCode("12211")
                .sellerCountryCode("SA")
                .currency("SAR")
                .taxCurrency("SAR")
                .lineExtensionAmount(new BigDecimal("150.00"))
                .taxExclusiveAmount(new BigDecimal("150.00"))
                .taxInclusiveAmount(new BigDecimal("172.47"))
                .prepaidAmount(BigDecimal.ZERO)
                .roundingAmount(new BigDecimal("-0.03"))
                .payableAmount(new BigDecimal("172.47"))
                .taxAmount(new BigDecimal("22.50"))
                .taxAmountAccountingCurrency(new BigDecimal("22.50"))
                .build();
    }

    private static List<ZatcaSimplifiedLine> buildSimplifiedLines() {
        ZatcaSimplifiedLine line = ZatcaSimplifiedLine.builder()
                .lineNumber(1)
                .itemCode("ITEM-001")
                .description("Retail item")
                .quantity(new BigDecimal("3.00000"))
                .itemNetPrice(new BigDecimal("50.00000"))
                .lineExtensionAmount(new BigDecimal("150.00"))
                .netAmount(new BigDecimal("150.00"))
                .vatAmount(new BigDecimal("22.50"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .build();
        return List.of(line);
    }

    @Test
    void standardRoundingAmount_negative_signConvention() {
        ZatcaStandardHeader header = ZatcaStandardHeader.builder()
                .id(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("STD-ROUND-001")
                .invoiceTypeCode("388")
                .transactionTypeCode("0100000")
                .issueDate(LocalDate.of(2026, 5, 20))
                .issueTime(LocalTime.of(10, 0, 0))
                .sellerData(sellerMap())
                .sellerVatNumber("300000000000003")
                .sellerCountryCode("SA")
                .buyerData(buyerBasicMap())
                .buyerCountryCode("SA")
                .currency("SAR")
                .taxCurrency("SAR")
                .lineExtensionAmount(new BigDecimal("100.00"))
                .taxExclusiveAmount(new BigDecimal("100.00"))
                .taxInclusiveAmount(new BigDecimal("114.97"))
                .prepaidAmount(BigDecimal.ZERO)
                .roundingAmount(new BigDecimal("-0.03"))
                .payableAmount(new BigDecimal("114.97"))
                .taxAmount(new BigDecimal("15.00"))
                .build();
        header.setLines(List.of());

        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        assertNotNull(result);
        assert xml.contains("<cbc:PayableRoundingAmount")
                : "Expected PayableRoundingAmount in UBL output";
        assert xml.contains("-0.03")
                : "Expected negative rounding amount -0.03";
    }

    @Test
    void standardNonSar_skipsSecondTaxTotal() {
        ZatcaStandardHeader header = ZatcaStandardHeader.builder()
                .id(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("STD-USD-001")
                .invoiceTypeCode("388")
                .transactionTypeCode("0100000")
                .issueDate(LocalDate.of(2026, 5, 20))
                .issueTime(LocalTime.of(10, 0, 0))
                .sellerData(sellerMap())
                .sellerVatNumber("300000000000003")
                .sellerCountryCode("SA")
                .buyerData(buyerBasicMap())
                .buyerCountryCode("SA")
                .currency("USD")
                .taxCurrency("SAR")
                .lineExtensionAmount(new BigDecimal("100.00"))
                .taxExclusiveAmount(new BigDecimal("100.00"))
                .taxInclusiveAmount(new BigDecimal("115.00"))
                .prepaidAmount(BigDecimal.ZERO)
                .payableAmount(new BigDecimal("115.00"))
                .taxAmount(new BigDecimal("15.00"))
                .taxAmountAccountingCurrency(BigDecimal.ZERO)
                .build();
        header.setLines(List.of());

        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        assertNotNull(result);
        long taxTotalCount = xml.split("<cac:TaxTotal>").length - 1;
        assert taxTotalCount == 1
                : "Expected exactly 1 TaxTotal for non-SAR with BT-111=0, got "
                        + taxTotalCount;
    }

    @Test
    void standardLine_emitsFullPriceBlock_withBaseQuantity() {
        ZatcaStandardHeader header = ZatcaStandardHeader.builder()
                .id(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("STD-LINEBLOCK-001")
                .invoiceTypeCode("388")
                .transactionTypeCode("0100000")
                .issueDate(LocalDate.of(2026, 5, 19))
                .issueTime(LocalTime.of(14, 30, 0))
                .sellerData(sellerMap())
                .sellerVatNumber("300000000000003")
                .sellerCountryCode("SA")
                .buyerData(buyerBasicMap())
                .buyerCountryCode("SA")
                .currency("SAR")
                .taxCurrency("SAR")
                .lineExtensionAmount(new BigDecimal("280.00"))
                .taxExclusiveAmount(new BigDecimal("280.00"))
                .taxInclusiveAmount(new BigDecimal("322.00"))
                .prepaidAmount(BigDecimal.ZERO)
                .payableAmount(new BigDecimal("322.00"))
                .taxAmount(new BigDecimal("42.00"))
                .build();
        ZatcaStandardLine line = ZatcaStandardLine.builder()
                .lineNumber(1)
                .itemCode("ITEM-001")
                .description("Item with line allowance")
                .unitType("PCE")
                .quantity(new BigDecimal("2.00000"))
                .itemNetPrice(new BigDecimal("150.00000"))
                .itemPriceBaseQuantity(new BigDecimal("1"))
                .lineExtensionAmount(new BigDecimal("280.00"))
                .netAmount(new BigDecimal("280.00"))
                .vatInclusiveAmount(new BigDecimal("322.00"))
                .vatAmount(new BigDecimal("42.00"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .build();
        ZatcaStandardLineAllowance allowance =
                ZatcaStandardLineAllowance.builder()
                        .sequence((short) 1)
                        .amount(new BigDecimal("20.00"))
                        .reason("Volume discount")
                        .build();
        line.setAllowances(List.of(allowance));
        header.setLines(List.of(line));

        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        assertNotNull(result);
        assert xml.contains(
                "<cbc:BaseQuantity unitCode=\"PCE\">1</cbc:BaseQuantity>")
                : "Expected BaseQuantity inside Price block";
        assert xml.contains(
                "<cbc:AllowanceChargeReason>Volume discount"
                        + "</cbc:AllowanceChargeReason>")
                : "Expected line-level AllowanceCharge with reason";
        assert xml.contains("<cbc:RoundingAmount>")
                : "Expected KSA-12 RoundingAmount in line TaxTotal";
    }

    @Test
    void simplifiedLine_emitsFullPriceBlock_withBaseQuantity() {
        ZatcaSimplifiedHeader header = ZatcaSimplifiedHeader.builder()
                .id(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("SIMP-LINEBLOCK-001")
                .invoiceTypeCode("388")
                .transactionTypeCode("0200000")
                .issueDate(LocalDate.of(2026, 5, 19))
                .issueTime(LocalTime.of(14, 30, 0))
                .sellerData(sellerMap())
                .sellerVatNumber("300000000000003")
                .sellerCountryCode("SA")
                .currency("SAR")
                .taxCurrency("SAR")
                .lineExtensionAmount(new BigDecimal("130.00"))
                .taxExclusiveAmount(new BigDecimal("130.00"))
                .taxInclusiveAmount(new BigDecimal("149.50"))
                .prepaidAmount(BigDecimal.ZERO)
                .payableAmount(new BigDecimal("149.50"))
                .taxAmount(new BigDecimal("19.50"))
                .build();
        ZatcaSimplifiedLine line = ZatcaSimplifiedLine.builder()
                .lineNumber(1)
                .itemCode("ITEM-001")
                .description("Retail item with allowance")
                .unitType("PCE")
                .quantity(new BigDecimal("3.00000"))
                .itemNetPrice(new BigDecimal("50.00000"))
                .itemPriceBaseQuantity(new BigDecimal("1"))
                .lineExtensionAmount(new BigDecimal("130.00"))
                .netAmount(new BigDecimal("130.00"))
                .vatInclusiveAmount(new BigDecimal("149.50"))
                .vatAmount(new BigDecimal("19.50"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .build();
        ZatcaSimplifiedLineAllowance allowance =
                ZatcaSimplifiedLineAllowance.builder()
                        .sequence((short) 1)
                        .amount(new BigDecimal("20.00"))
                        .reason("Loyalty discount")
                        .build();
        line.setAllowances(List.of(allowance));
        header.setLines(List.of(line));

        byte[] result = builder.buildSimplifiedUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        assertNotNull(result);
        assert xml.contains(
                "<cbc:BaseQuantity unitCode=\"PCE\">1</cbc:BaseQuantity>")
                : "Expected BaseQuantity inside Price block";
        assert xml.contains(
                "<cbc:AllowanceChargeReason>Loyalty discount"
                        + "</cbc:AllowanceChargeReason>")
                : "Expected line-level AllowanceCharge with reason";
        assert xml.contains("<cbc:RoundingAmount>")
                : "Expected KSA-12 RoundingAmount in line TaxTotal";
    }
}
