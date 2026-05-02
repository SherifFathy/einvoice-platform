package com.einvoice.zatca.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.InvoiceVatBreakdown;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class ZatcaUblBuilderGoldenFileTest {

    private ZatcaUblBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ZatcaUblBuilder();
    }

    @Test
    void shouldBuildTaxInvoiceXmlMatchingGoldenFile() throws Exception {
        Invoice invoice = createTestInvoice(InvoiceType.TAX_INVOICE);
        String xml = builder.buildXml(invoice);

        assertNotNull(xml);
        assertTrue(xml.contains("<Invoice"));
        assertTrue(xml.contains("clearance:1.0"));
        assertTrue(xml.contains("<cbc:InvoiceTypeCode"));
        assertTrue(xml.contains("388"));
        assertTrue(xml.contains("<cac:AccountingSupplierParty"));
        assertTrue(xml.contains("<cac:AccountingCustomerParty"));
        assertTrue(xml.contains("<cac:InvoiceLine"));
        assertTrue(xml.contains("<cac:ClassifiedTaxCategory"));

        String expectedXml = loadGoldenFile("tax-invoice.xml");
        assertCanonicalMatch(expectedXml, xml);
    }

    @Test
    void shouldBuildSimplifiedInvoiceXml() {
        Invoice invoice = createTestInvoice(InvoiceType.SIMPLIFIED_TAX_INVOICE);
        String xml = builder.buildXml(invoice);

        assertNotNull(xml);
        assertTrue(xml.contains("reporting:1.0"));
        assertTrue(xml.contains("386"));
    }

    @Test
    void shouldBuildCreditNoteXml() {
        Invoice invoice = createTestInvoice(InvoiceType.CREDIT_NOTE);
        String xml = builder.buildXml(invoice);

        assertNotNull(xml);
        assertTrue(xml.contains("381"));
    }

    @Test
    void shouldBuildDebitNoteXml() {
        Invoice invoice = createTestInvoice(InvoiceType.DEBIT_NOTE);
        String xml = builder.buildXml(invoice);

        assertNotNull(xml);
        assertTrue(xml.contains("383"));
    }

    @Test
    void shouldIncludeAllNamespaces() {
        Invoice invoice = createTestInvoice(InvoiceType.TAX_INVOICE);
        String xml = builder.buildXml(invoice);

        assertTrue(xml.contains("xmlns:cbc"));
        assertTrue(xml.contains("xmlns:cac"));
        assertTrue(xml.contains("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"));
    }

    @Test
    void shouldIncludeSellerAddress() {
        Invoice invoice = createTestInvoice(InvoiceType.TAX_INVOICE);
        String xml = builder.buildXml(invoice);

        assertTrue(xml.contains("<cbc:StreetName"));
        assertTrue(xml.contains("<cbc:CityName"));
        assertTrue(xml.contains("<cbc:IdentificationCode"));
    }

    @Test
    void shouldIncludeTaxBreakdown() {
        Invoice invoice = createTestInvoice(InvoiceType.TAX_INVOICE);
        String xml = builder.buildXml(invoice);

        assertTrue(xml.contains("<cac:TaxTotal"));
        assertTrue(xml.contains("<cac:TaxSubtotal"));
        assertTrue(xml.contains("<cbc:ID>S</cbc:ID>"));
    }

    @Test
    void shouldIncludeMonetaryTotals() {
        Invoice invoice = createTestInvoice(InvoiceType.TAX_INVOICE);
        String xml = builder.buildXml(invoice);

        assertTrue(xml.contains("<cac:LegalMonetaryTotal"));
        assertTrue(xml.contains("<cbc:LineExtensionAmount"));
        assertTrue(xml.contains("<cbc:PayableAmount"));
    }

    private Invoice createTestInvoice(InvoiceType type) {
        Company company = Company.builder()
                .id(1L)
                .nameAr("شركة الاختبار")
                .nameEn("Test Company LLC")
                .vatNumber("300000000000003")
                .crNumber("1234567890")
                .build();

        Branch branch = Branch.builder()
                .id(1L)
                .company(company)
                .nameAr("فرع الرياض")
                .nameEn("Riyadh Branch")
                .branchCode("RIY-001")
                .street("King Fahd Road")
                .buildingNumber("1234")
                .district("Al Olaya")
                .city("Riyadh")
                .postalCode("12211")
                .countryCode("SA")
                .build();

        Customer buyer = Customer.builder()
                .id(1L)
                .company(company)
                .nameEn("Buyer Company")
                .nameAr("شركة المشتري")
                .vatNumber("300000000100003")
                .customerType(CustomerType.B2B)
                .street("Prince Sultan Road")
                .buildingNumber("5678")
                .city("Jeddah")
                .district("Al Hamra")
                .postalCode("21477")
                .countryCode("SA")
                .build();

        InvoiceLine line = InvoiceLine.builder()
                .descriptionEn("Consulting Services")
                .descriptionAr("خدمات استشارية")
                .quantity(new BigDecimal("2"))
                .unit("EA")
                .unitPrice(new BigDecimal("500.00"))
                .discountAmount(BigDecimal.ZERO)
                .vatCategory("S")
                .vatRate(new BigDecimal("15.00"))
                .lineNetAmount(new BigDecimal("1000.00"))
                .lineVatAmount(new BigDecimal("150.00"))
                .lineTotal(new BigDecimal("1150.00"))
                .sortOrder(1)
                .build();

        InvoiceVatBreakdown vatBreakdown = InvoiceVatBreakdown.builder()
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("15.00"))
                .taxableAmount(new BigDecimal("1000.00"))
                .taxAmount(new BigDecimal("150.00"))
                .build();

        Invoice invoice = Invoice.builder()
                .id(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"))
                .company(company)
                .branch(branch)
                .invoiceNumber("RIY-001-00001")
                .type(type)
                .subtypeFlags("{}")
                .issueDate(LocalDate.of(2026, 4, 15))
                .supplyDate(LocalDate.of(2026, 4, 15))
                .currency("SAR")
                .buyer(buyer)
                .paymentMeansCode("10")
                .totalLineNet(new BigDecimal("1000.00"))
                .totalAllowances(BigDecimal.ZERO)
                .totalWithoutVat(new BigDecimal("1000.00"))
                .totalVat(new BigDecimal("150.00"))
                .totalWithVat(new BigDecimal("1150.00"))
                .amountDue(new BigDecimal("1150.00"))
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .lines(List.of(line))
                .vatBreakdown(List.of(vatBreakdown))
                .build();

        return invoice;
    }

    private String loadGoldenFile(String filename) {
        try (InputStream is = getClass().getResourceAsStream("/golden-files/" + filename)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private void assertCanonicalMatch(String expected, String actual) throws Exception {
        if (expected == null) {
            throw new AssertionError("Golden file missing — regenerate tax-invoice.xml");
        }
        String canonicalExpected = canonicalize(expected);
        String canonicalActual = canonicalize(actual);
        assertEquals(canonicalExpected, canonicalActual,
                "UBL XML diverged from golden file. Inspect the diff and, if intentional, "
                        + "regenerate src/test/resources/golden-files/tax-invoice.xml");
    }

    private String canonicalize(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory f =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
        Document doc = b.parse(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)));
        org.apache.xml.security.Init.init();
        org.apache.xml.security.c14n.Canonicalizer canon =
                org.apache.xml.security.c14n.Canonicalizer.getInstance(
                        org.apache.xml.security.c14n.Canonicalizer
                                .ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        canon.canonicalizeSubtree(doc.getDocumentElement(), baos);
        return baos.toString(StandardCharsets.UTF_8);
    }
}
