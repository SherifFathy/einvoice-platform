package com.einvoice.eta.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EtaInvoiceSerializerGoldenFileTest {

    private EtaInvoiceSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        serializer = new EtaInvoiceSerializer(objectMapper);
    }

    @Test
    void serializeTaxInvoice_matchesGoldenFile() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.TAX_INVOICE);
        String result = serializer.serialize(invoice);

        assertNotNull(result);
        JsonNode actual = objectMapper.readTree(result);

        InputStream goldenStream = getClass().getResourceAsStream(
                "/golden-files/expected-eta-invoice.json");
        assertNotNull(goldenStream, "Golden file not found");
        JsonNode golden = objectMapper.readTree(goldenStream);

        assertJsonEquals(golden, actual, "root");
    }

    @Test
    void serializeCreditNote_usesLowercaseTypeCode() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.CREDIT_NOTE);
        String result = serializer.serialize(invoice);

        JsonNode actual = objectMapper.readTree(result);
        JsonNode doc = actual.path("documents").get(0);
        assertEquals("c", doc.path("documentTypeCode").asText());
    }

    @Test
    void serializeDebitNote_usesLowercaseTypeCode() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.DEBIT_NOTE);
        String result = serializer.serialize(invoice);

        JsonNode actual = objectMapper.readTree(result);
        JsonNode doc = actual.path("documents").get(0);
        assertEquals("d", doc.path("documentTypeCode").asText());
    }

    @Test
    void serializeSimplifiedTaxInvoice_usesLowercaseTypeCode() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.SIMPLIFIED_TAX_INVOICE);
        String result = serializer.serialize(invoice);

        JsonNode actual = objectMapper.readTree(result);
        JsonNode doc = actual.path("documents").get(0);
        assertEquals("s", doc.path("documentTypeCode").asText());
    }

    @Test
    void serialize_noDuplicateTotalFields() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.TAX_INVOICE);
        String result = serializer.serialize(invoice);

        JsonNode actual = objectMapper.readTree(result);
        JsonNode document = actual.path("documents").get(0).path("document");

        assertTrue(document.has("totalSalesAmount"), "Should have totalSalesAmount");
        assertTrue(document.has("totalDiscountAmount"), "Should have totalDiscountAmount");
        assertTrue(document.has("netAmount"), "Should have netAmount");
        assertTrue(document.has("totalItemsDiscountAmount"),
                "Should have totalItemsDiscountAmount");
        assertTrue(document.has("totalAmount"), "Should have totalAmount");

        assertEquals(0.0,
                document.path("totalItemsDiscountAmount").asDouble(), 0.001,
                "totalItemsDiscountAmount should be zero");
    }

    @Test
    void serialize_taxesCorrect() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.TAX_INVOICE);
        String result = serializer.serialize(invoice);

        JsonNode actual = objectMapper.readTree(result);
        JsonNode document = actual.path("documents").get(0).path("document");

        JsonNode taxTotals = document.path("taxTotals");
        assertEquals(1, taxTotals.size());
        assertEquals("VAT", taxTotals.get(0).path("taxType").asText());
        assertEquals(140.00, taxTotals.get(0).path("amount").asDouble(), 0.01);

        assertEquals(1140.00, document.path("totalAmount").asDouble(), 0.01);
    }

    @Test
    void serialize_invoiceLinesCorrect() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.TAX_INVOICE);
        String result = serializer.serialize(invoice);

        JsonNode actual = objectMapper.readTree(result);
        JsonNode lines = actual.path("documents").get(0)
                .path("document").path("invoiceLines");

        assertEquals(1, lines.size());
        JsonNode line = lines.get(0);
        assertEquals(1, line.path("number").asInt());
        assertEquals("Consulting services", line.path("description").asText());
        assertEquals(10.0, line.path("quantity").asDouble(), 0.001);
        assertEquals(100.0, line.path("unitValue").asDouble(), 0.001);

        JsonNode taxableItems = line.path("taxableItems");
        assertEquals(1, taxableItems.size());
        assertEquals("VAT", taxableItems.get(0).path("taxType").asText());
        assertEquals(14.0, taxableItems.get(0).path("rate").asDouble(), 0.1);
    }

    @Test
    void serialize_taxpayerFieldsCorrect() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.TAX_INVOICE);
        String result = serializer.serialize(invoice);

        JsonNode actual = objectMapper.readTree(result);
        JsonNode taxpayer = actual.path("documents").get(0).path("taxpayer");

        assertEquals("Test Company", taxpayer.path("name").asText());
        assertEquals("B", taxpayer.path("type").asText());
        assertEquals("300000000000001", taxpayer.path("id").path("id").asText());
        assertEquals("VAT", taxpayer.path("id").path("schemeID").asText());
        assertEquals("EG", taxpayer.path("address").path("country").asText());
    }

    @Test
    void serialize_receiverFieldsCorrect() throws Exception {
        Invoice invoice = buildTestInvoice(InvoiceType.TAX_INVOICE);
        String result = serializer.serialize(invoice);

        JsonNode actual = objectMapper.readTree(result);
        JsonNode receiver = actual.path("documents").get(0).path("receiver");

        assertEquals("Test Buyer", receiver.path("name").asText());
        assertEquals("B", receiver.path("type").asText());
        assertEquals("EG", receiver.path("address").path("country").asText());
    }

    private void assertJsonEquals(JsonNode expected, JsonNode actual, String path) {
        if (expected.isObject()) {
            assertTrue(actual.isObject(), "Expected object at " + path);
            expected.fieldNames().forEachRemaining(field -> {
                assertTrue(actual.has(field),
                        "Missing field '" + field + "' at " + path);
                assertJsonEquals(expected.get(field), actual.get(field),
                        path + "." + field);
            });
        } else if (expected.isArray()) {
            assertTrue(actual.isArray(), "Expected array at " + path);
            assertEquals(expected.size(), actual.size(),
                    "Array size mismatch at " + path);
            for (int i = 0; i < expected.size(); i++) {
                assertJsonEquals(expected.get(i), actual.get(i),
                        path + "[" + i + "]");
            }
        } else if (expected.isNumber()) {
            assertEquals(expected.asDouble(), actual.asDouble(), 0.01,
                    "Value mismatch at " + path);
        } else {
            assertEquals(expected.asText(), actual.asText(),
                    "Value mismatch at " + path);
        }
    }

    private Invoice buildTestInvoice(InvoiceType type) {
        Company company = Company.builder()
                .id(1L)
                .nameEn("Test Company")
                .vatNumber("300000000000001")
                .build();
        Branch branch = Branch.builder().id(1L).company(company).build();
        Customer buyer = Customer.builder()
                .id(1L)
                .nameEn("Test Buyer")
                .vatNumber("300000000000002")
                .build();

        Invoice invoice = Invoice.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .company(company)
                .branch(branch)
                .buyer(buyer)
                .invoiceNumber("INV-001")
                .type(type)
                .authority(Authority.ETA)
                .environment(Environment.ETA_PREPRODUCTION)
                .issueDate(LocalDate.of(2026, 4, 17))
                .totalLineNet(new BigDecimal("1000.00"))
                .totalWithoutVat(new BigDecimal("1000.00"))
                .totalVat(new BigDecimal("140.00"))
                .totalWithVat(new BigDecimal("1140.00"))
                .amountDue(new BigDecimal("1140.00"))
                .totalAllowances(BigDecimal.ZERO)
                .prepaidAmount(BigDecimal.ZERO)
                .createdAt(OffsetDateTime.parse("2026-04-17T00:00:00Z"))
                .build();

        InvoiceLine line = InvoiceLine.builder()
                .descriptionEn("Consulting services")
                .quantity(new BigDecimal("10.00"))
                .unit("EA")
                .unitPrice(new BigDecimal("100.0000"))
                .discountAmount(BigDecimal.ZERO)
                .vatCategory("S")
                .vatRate(new BigDecimal("14.0"))
                .lineVatAmount(new BigDecimal("140.00"))
                .lineNetAmount(new BigDecimal("1000.00"))
                .lineTotal(new BigDecimal("1140.00"))
                .sortOrder(1)
                .invoice(invoice)
                .build();

        invoice.setLines(List.of(line));
        return invoice;
    }
}
