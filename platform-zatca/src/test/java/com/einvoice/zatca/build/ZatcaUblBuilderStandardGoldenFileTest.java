package com.einvoice.zatca.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardLine;
import com.einvoice.core.domain.zatca.ZatcaStandardLineAllowance;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZatcaUblBuilderStandardGoldenFileTest {

    private ZatcaUblBuilder builder;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        builder = new ZatcaUblBuilder();
    }

    @Test
    void standardTaxInvoice_0100000() throws IOException {
        ZatcaStandardHeader header = buildStandardHeader("0100000", "388");
        header.setLines(List.of(buildLine(1, "ITEM-001", "Service fee",
                new BigDecimal("2.00000"), new BigDecimal("150.00000"),
                "S", new BigDecimal("15.00"))));

        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);

        writeGoldenFile("standard-tax-invoice-0100000.xml", xml);
        assertThat(xml).contains("<cbc:InvoiceTypeCode");
        assertThat(xml).contains(">388</cbc:InvoiceTypeCode>");
        assertThat(xml).contains("<cac:AccountingCustomerParty>");
        assertThat(xml).contains("<cbc:InvoicedQuantity>2.00000</cbc:InvoicedQuantity>");
    }

    @Test
    void selfBilled_0100001() throws IOException {
        ZatcaStandardHeader header = buildStandardHeader("0100001", "388");
        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("standard-self-billed-0100001.xml", xml);
        assertThat(xml).contains("0100001");
    }

    @Test
    void thirdParty_0100010() throws IOException {
        ZatcaStandardHeader header = buildStandardHeader("0100010", "388");
        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("standard-third-party-0100010.xml", xml);
        assertThat(xml).contains("0100010");
    }

    @Test
    void export_0100100() throws IOException {
        ZatcaStandardHeader header = buildStandardHeader("0100100", "388");
        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("standard-export-0100100.xml", xml);
        assertThat(xml).contains("0100100");
    }

    @Test
    void creditNote_381() throws IOException {
        ZatcaStandardHeader header = buildStandardHeader("0100000", "381");
        header.setOriginalInvoiceId(FIXED_ORIGINAL_ID);
        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("standard-credit-note-381.xml", xml);
        assertThat(xml).contains("<cbc:InvoiceTypeCode");
        assertThat(xml).contains(">381</cbc:InvoiceTypeCode>");
        assertThat(xml).contains("<cac:BillingReference>");
        assertThat(xml).contains(FIXED_ORIGINAL_ID.toString());
    }

    @Test
    void debitNote_383() throws IOException {
        ZatcaStandardHeader header = buildStandardHeader("0100000", "383");
        header.setOriginalInvoiceId(FIXED_ORIGINAL_ID);
        byte[] result = builder.buildStandardUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("standard-debit-note-383.xml", xml);
        assertThat(xml).contains("<cbc:InvoiceTypeCode");
        assertThat(xml).contains(">383</cbc:InvoiceTypeCode>");
        assertThat(xml).contains("<cac:BillingReference>");
    }

    @Test
    void standardLine_emitsFullPriceBlock_withBaseQuantity()
            throws IOException {
        ZatcaStandardHeader header = buildStandardHeader("0100000", "388");
        ZatcaStandardLine line = new ZatcaStandardLine();
        line.setLineNumber(1);
        line.setItemCode("ITEM-001");
        line.setDescription("Item with line allowance");
        line.setUnitType("PCE");
        line.setQuantity(new BigDecimal("2.00000"));
        line.setItemNetPrice(new BigDecimal("150.00000"));
        line.setItemPriceBaseQuantity(new BigDecimal("1"));
        line.setLineExtensionAmount(new BigDecimal("280.00"));
        line.setNetAmount(new BigDecimal("280.00"));
        line.setVatInclusiveAmount(new BigDecimal("322.00"));
        line.setVatAmount(new BigDecimal("42.00"));
        line.setVatCategoryCode("S");
        line.setVatRate(new BigDecimal("15.00"));
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
        assertThat(xml).contains(
                "<cbc:BaseQuantity unitCode=\"PCE\">1</cbc:BaseQuantity>");
        assertThat(xml).contains(
                "<cbc:AllowanceChargeReason>Volume discount"
                        + "</cbc:AllowanceChargeReason>");
        assertThat(xml).contains("<cbc:RoundingAmount>");
        assertThat(xml).contains("322.00</cbc:RoundingAmount>");
    }

    private static final UUID FIXED_HEADER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FIXED_COMPANY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FIXED_ORIGINAL_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private ZatcaStandardHeader buildStandardHeader(String txTypeCode,
            String invoiceTypeCode) {
        ZatcaStandardHeader header = new ZatcaStandardHeader();
        header.setId(FIXED_HEADER_ID);
        header.setCompanyId(FIXED_COMPANY_ID);
        header.setAuthorityEnvironmentId((short) 5);
        header.setInvoiceNumber("STD-GOLDEN-001");
        header.setInvoiceTypeCode(invoiceTypeCode);
        header.setTransactionTypeCode(txTypeCode);
        header.setIssueDate(LocalDate.of(2026, 5, 19));
        header.setIssueTime(LocalTime.of(14, 30, 0));
        header.setSellerData(Map.of(
                "taxRegistrationNumber", "300000000000003",
                "partyName", "Test Seller Co.",
                "addressStreet", "King Fahd Road",
                "addressCityName", "Riyadh",
                "addressPostalZone", "12211",
                "addressCountryCode", "SA"));
        header.setSellerVatNumber("300000000000003");
        header.setSellerPostalCode("12211");
        header.setSellerCountryCode("SA");
        header.setBuyerData(Map.of(
                "taxRegistrationNumber", "300000000100003",
                "partyName", "Test Buyer Co.",
                "addressCityName", "Jeddah",
                "addressCountryCode", "SA"));
        header.setBuyerVatNumber("300000000100003");
        header.setBuyerCountryCode("SA");
        header.setCurrency("SAR");
        header.setTaxCurrency("SAR");
        header.setLineExtensionAmount(new BigDecimal("300.00"));
        header.setTaxExclusiveAmount(new BigDecimal("300.00"));
        header.setTaxAmount(new BigDecimal("45.00"));
        header.setTaxInclusiveAmount(new BigDecimal("345.00"));
        header.setPayableAmount(new BigDecimal("345.00"));
        header.setPrepaidAmount(BigDecimal.ZERO);
        
        header.setStatus(DocumentState.DRAFT);
        header.setLines(new ArrayList<>());
        return header;
    }

    private ZatcaStandardLine buildLine(int lineNumber, String itemCode,
            String description, BigDecimal quantity,
            BigDecimal unitPrice, String vatCategory,
            BigDecimal vatAmount) {
        ZatcaStandardLine line = new ZatcaStandardLine();
        line.setLineNumber(lineNumber);
        line.setItemCode(itemCode);
        line.setDescription(description);
        line.setQuantity(quantity);
        line.setItemNetPrice(unitPrice);
        line.setLineExtensionAmount(quantity.multiply(unitPrice)
                .setScale(2, java.math.RoundingMode.HALF_EVEN));
        line.setNetAmount(quantity.multiply(unitPrice)
                .setScale(2, java.math.RoundingMode.HALF_EVEN));
        line.setVatCategoryCode(vatCategory);
        line.setVatRate(new BigDecimal("15.00"));
        line.setVatAmount(vatAmount);
        return line;
    }

    private void writeGoldenFile(String filename, String content)
            throws IOException {
        String resourcePath = "golden/standard/" + filename;
        if ("1".equals(System.getenv("UPDATE_GOLDEN"))) {
            Path file = resolveGoldenWriteDir().resolve(filename);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } else {
            var stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resourcePath);
            assertThat(stream)
                    .as("golden resource %s on test classpath",
                            resourcePath)
                    .isNotNull();
            String expected = new String(stream.readAllBytes(),
                    StandardCharsets.UTF_8);
            assertThat(content).isEqualToNormalizingNewlines(expected);
        }
    }

    private static Path resolveGoldenWriteDir() {
        Path moduleLocal = Path.of(
                "src/test/resources/golden/standard");
        if (Files.isDirectory(Path.of("src/test/resources"))) {
            return moduleLocal;
        }
        return Path.of(
                "platform-zatca/src/test/resources/golden/standard");
    }
}
