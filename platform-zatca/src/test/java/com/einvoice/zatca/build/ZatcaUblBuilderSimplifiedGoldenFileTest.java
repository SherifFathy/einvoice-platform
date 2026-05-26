package com.einvoice.zatca.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedLine;
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

class ZatcaUblBuilderSimplifiedGoldenFileTest {

    private ZatcaUblBuilder builder;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        builder = new ZatcaUblBuilder();
    }

    @Test
    void standardSimplified_0200000() throws IOException {
        ZatcaSimplifiedHeader header = buildSimplifiedHeader(
                "0200000", "388");
        header.setBuyerData(null);
        header.setLines(List.of(buildLine(1, "ITEM-001", "Retail item",
                new BigDecimal("3.00000"), new BigDecimal("50.00000"),
                "S", new BigDecimal("22.50"))));

        byte[] result = builder.buildSimplifiedUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);

        writeGoldenFile("simplified-standard-0200000.xml", xml);
        assertThat(xml).contains(
                "<cbc:InvoiceTypeCode name=\"0200000\">388"
                        + "</cbc:InvoiceTypeCode>");
        assertThat(xml).doesNotContain("<cac:AccountingCustomerParty>");
        assertThat(xml).contains("<cbc:InvoicedQuantity>3.00000"
                + "</cbc:InvoicedQuantity>");
    }

    @Test
    void standardSimplified_withBuyer() throws IOException {
        ZatcaSimplifiedHeader header = buildSimplifiedHeader(
                "0200000", "388");
        header.setBuyerData(Map.of(
                "taxRegistrationNumber", "300000000200003",
                "partyName", "Walk-in Customer"));
        header.setBuyerVatNumber("300000000200003");
        byte[] result = builder.buildSimplifiedUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("simplified-with-buyer-0200000.xml", xml);
        assertThat(xml).contains("<cac:AccountingCustomerParty>");
    }

    @Test
    void creditNote_381() throws IOException {
        ZatcaSimplifiedHeader header = buildSimplifiedHeader(
                "0200000", "381");
        header.setBuyerData(null);
        UUID originalId = FIXED_SIMPLIFIED_ORIGINAL_ID;
        header.setOriginalInvoiceId(originalId);
        byte[] result = builder.buildSimplifiedUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("simplified-credit-note-381.xml", xml);
        assertThat(xml).contains("<cbc:InvoiceTypeCode");
        assertThat(xml).contains(">381</cbc:InvoiceTypeCode>");
        assertThat(xml).contains("<cac:BillingReference>");
        assertThat(xml).contains(originalId.toString());
    }

    @Test
    void debitNote_383() throws IOException {
        ZatcaSimplifiedHeader header = buildSimplifiedHeader(
                "0200000", "383");
        header.setBuyerData(null);
        UUID originalId = FIXED_SIMPLIFIED_ORIGINAL_ID;
        header.setOriginalInvoiceId(originalId);
        byte[] result = builder.buildSimplifiedUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("simplified-debit-note-383.xml", xml);
        assertThat(xml).contains("<cbc:InvoiceTypeCode");
        assertThat(xml).contains(">383</cbc:InvoiceTypeCode>");
        assertThat(xml).contains("<cac:BillingReference>");
    }

    @Test
    void noBuyerBlockWhenEmpty() throws IOException {
        ZatcaSimplifiedHeader header = buildSimplifiedHeader(
                "0200000", "388");
        header.setBuyerData(null);
        byte[] result = builder.buildSimplifiedUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        writeGoldenFile("simplified-no-buyer.xml", xml);
        assertThat(xml).doesNotContain("<cac:AccountingCustomerParty>");
    }

    @Test
    void noBuyerBlockWhenEmptyMap() throws IOException {
        ZatcaSimplifiedHeader header = buildSimplifiedHeader(
                "0200000", "388");
        header.setBuyerData(Map.of());
        byte[] result = builder.buildSimplifiedUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        assertThat(xml).doesNotContain("<cac:AccountingCustomerParty>");
    }

    @Test
    void namespacePrefixesConsistent() throws IOException {
        ZatcaSimplifiedHeader header = buildSimplifiedHeader(
                "0200000", "388");
        header.setBuyerData(null);
        byte[] result = builder.buildSimplifiedUbl(header);
        String xml = new String(result, StandardCharsets.UTF_8);
        assertThat(xml).doesNotMatch(
                "(?s)<cbc:[^>]+>.*</(?!cbc:)[^>]+>");
        assertThat(xml).contains("<cbc:DocumentCurrencyCode>");
        assertThat(xml).contains("</cbc:DocumentCurrencyCode>");
        assertThat(xml).contains("<cbc:TaxCurrencyCode>");
        assertThat(xml).contains("</cbc:TaxCurrencyCode>");
    }

    private static final UUID FIXED_SIMPLIFIED_HEADER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FIXED_SIMPLIFIED_COMPANY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID FIXED_SIMPLIFIED_ORIGINAL_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000005");

    private ZatcaSimplifiedHeader buildSimplifiedHeader(
            String txTypeCode, String invoiceTypeCode) {
        ZatcaSimplifiedHeader header = new ZatcaSimplifiedHeader();
        header.setId(FIXED_SIMPLIFIED_HEADER_ID);
        header.setCompanyId(FIXED_SIMPLIFIED_COMPANY_ID);
        header.setAuthorityEnvironmentId((short) 5);
        header.setInvoiceNumber("SIMP-GOLDEN-001");
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
        header.setBuyerData(null);
        header.setCurrency("SAR");
        header.setTaxCurrency("SAR");
        header.setLineExtensionAmount(new BigDecimal("150.00"));
        header.setTaxExclusiveAmount(new BigDecimal("150.00"));
        header.setTaxAmount(new BigDecimal("22.50"));
        header.setTaxInclusiveAmount(new BigDecimal("172.50"));
        header.setPayableAmount(new BigDecimal("172.50"));
        header.setPrepaidAmount(BigDecimal.ZERO);
        
        header.setStatus(DocumentState.DRAFT);
        header.setLines(new ArrayList<>());
        return header;
    }

    private ZatcaSimplifiedLine buildLine(int lineNumber, String itemCode,
            String description, BigDecimal quantity,
            BigDecimal unitPrice, String vatCategory,
            BigDecimal vatAmount) {
        ZatcaSimplifiedLine line = new ZatcaSimplifiedLine();
        line.setLineNumber(lineNumber);
        line.setItemCode(itemCode);
        line.setDescription(description);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setLineExtensionAmount(quantity.multiply(unitPrice)
                .setScale(2, java.math.RoundingMode.HALF_EVEN));
        line.setDiscountAmount(BigDecimal.ZERO);
        line.setAllowanceAmount(BigDecimal.ZERO);
        line.setNetAmount(quantity.multiply(unitPrice)
                .setScale(2, java.math.RoundingMode.HALF_EVEN));
        line.setVatCategoryCode(vatCategory);
        line.setVatRate(new BigDecimal("15.00"));
        line.setVatAmount(vatAmount);
        return line;
    }

    private void writeGoldenFile(String filename, String content)
            throws IOException {
        String resourcePath = "golden/simplified/" + filename;
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
                "src/test/resources/golden/simplified");
        if (Files.isDirectory(Path.of("src/test/resources"))) {
            return moduleLocal;
        }
        return Path.of(
                "platform-zatca/src/test/resources/golden/simplified");
    }
}
