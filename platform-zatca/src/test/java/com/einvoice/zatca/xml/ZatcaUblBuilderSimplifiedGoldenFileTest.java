package com.einvoice.zatca.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ZatcaUblBuilderSimplifiedGoldenFileTest {

    private ZatcaUblBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ZatcaUblBuilder();
    }

    @Test
    void shouldBuildSimplifiedInvoiceXmlMatchingGoldenFile() throws Exception {
        Invoice invoice = createSimplifiedInvoice();
        String xml = builder.buildXml(invoice);

        assertNotNull(xml);
        assertTrue(xml.contains("<Invoice"));
        assertTrue(xml.contains("reporting:1.0"));
        assertTrue(xml.contains("<cbc:InvoiceTypeCode"));
        assertTrue(xml.contains("386"));
        assertTrue(xml.contains("<cac:AccountingSupplierParty"));
        assertTrue(xml.contains("<cac:InvoiceLine"));
        assertTrue(xml.contains("<cac:LegalMonetaryTotal"));

        String expectedXml = loadGoldenFile("simplified-invoice.xml");
        assertCanonicalMatch(expectedXml, xml);
    }

    @Test
    void shouldUseReportingProfileId() {
        Invoice invoice = createSimplifiedInvoice();
        String xml = builder.buildXml(invoice);

        assertTrue(xml.contains("<cbc:ProfileID>reporting:1.0</cbc:ProfileID>"));
        assertTrue(xml.contains("clearance:1.0") == false);
    }

    @Test
    void shouldUseTypeCode386() {
        Invoice invoice = createSimplifiedInvoice();
        String xml = builder.buildXml(invoice);

        assertTrue(xml.contains(">386</cbc:InvoiceTypeCode>"));
    }

    @Test
    void shouldIncludeAllNamespacesForSimplified() {
        Invoice invoice = createSimplifiedInvoice();
        String xml = builder.buildXml(invoice);

        assertTrue(xml.contains("xmlns:cbc"));
        assertTrue(xml.contains("xmlns:cac"));
        assertTrue(xml.contains("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"));
    }

    @Test
    void shouldIncludeQrPlaceholderForSimplified() {
        Invoice invoice = createSimplifiedInvoice();
        String xml = builder.buildXml(invoice);

        assertTrue(xml.contains("<cbc:ID>QR</cbc:ID>"));
        assertTrue(xml.contains("<cbc:EmbeddedDocumentBinaryObject"));
    }

    @Test
    void shouldIncludeIcvAndPihReferences() {
        Invoice invoice = createSimplifiedInvoice();
        String pih = "NWZlY2ViNjZmZmM4NmYzOGQ5NTI3ODZjNmQ2OTZjNzljMmRiYzIzOWRk"
                + "NGU5MWI0NjcyOWQ3M2EyN2ZiNTdlOQ==";
        String xml = builder.buildXml(invoice, 5L, pih);

        assertTrue(xml.contains("<cbc:ID>ICV</cbc:ID>"));
        assertTrue(xml.contains("<cbc:UUID>5</cbc:UUID>"));
        assertTrue(xml.contains("<cbc:ID>PIH</cbc:ID>"));
        assertTrue(xml.contains(pih));
    }

    private Invoice createSimplifiedInvoice() {
        Company company = Company.builder()
                .id(1L)
                .nameAr("شركة الاختبار")
                .nameEn("Test Company LLC")
                .vatNumber("300000000000003")
                .crNumber("1234567890")
                .street("King Fahd Road")
                .buildingNumber("1234")
                .district("Al Olaya")
                .city("Riyadh")
                .postalCode("12211")
                .countryCode("SA")
                .build();

        Branch branch = Branch.builder()
                .id(1L)
                .company(company)
                .nameAr("فرع الرياض")
                .nameEn("Riyadh Branch")
                .branchCode("RIY-001")
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

        return Invoice.builder()
                .id(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"))
                .company(company)
                .branch(branch)
                .invoiceNumber("RIY-001-00001")
                .type(InvoiceType.SIMPLIFIED_TAX_INVOICE)
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
            throw new AssertionError("Golden file missing — regenerate " + "simplified-invoice.xml");
        }
        String canonicalExpected = canonicalize(expected);
        String canonicalActual = canonicalize(actual);
        assertEquals(canonicalExpected, canonicalActual,
                "Simplified invoice UBL XML diverged from golden file. Inspect the diff and, "
                        + "if intentional, regenerate src/test/resources/golden-files/simplified-invoice.xml");
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
