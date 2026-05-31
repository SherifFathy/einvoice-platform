package com.einvoice.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.AuthorityEnvironment;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ZatcaStandardIngestionIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("einvoice_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static final String ENDPOINT = "/api/integration/v1/zatca/standard";
    private static final String TEST_TAX_NUMBER = "100200300";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private AuthorityEnvironmentRepository authorityEnvironmentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Value("${einvoice.integration.gateway-principal-id}") private UUID gatewayPrincipalId;

    private UUID companyId;
    private short authorityEnvId;

    @BeforeEach
    void setUp() {
        companyId = companyRepository.save(
                Company.builder()
                        .nameEn("ZATCA Integration Test Co")
                        .nameAr("ZATCA Integration Test Co AR")
                        .taxNumber(TEST_TAX_NUMBER)
                        .isActive(true)
                        .build()).getId();

        AuthorityEnvironment ae = authorityEnvironmentRepository
                .findByAuthorityAndEnvironmentAndIsActiveTrue("ZATCA", "SANDBOX")
                .orElseThrow();
        authorityEnvId = ae.getId();

        jdbcTemplate.update(
                "INSERT INTO zatca_configs "
                        + "(id, company_id, authority_environment_id, private_key, device_uuid, "
                        + "csr, compliance_certificate, compliance_api_secret) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), companyId.toString(), authorityEnvId,
                "test-private-key", "test-device-uuid",
                "test-csr", "test-compliance-cert", "test-compliance-secret");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        try {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE company_id = ?", companyId);
            jdbcTemplate.update("DELETE FROM inbound_payload_archive WHERE endpoint LIKE ?",
                    "/api/integration/v1/zatca/%");
            jdbcTemplate.update("DELETE FROM zatca_standard_line_allowances WHERE line_id IN "
                    + "(SELECT l.id FROM zatca_standard_lines l JOIN zatca_standard_headers h "
                    + "ON l.header_id = h.id WHERE h.company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_lines WHERE header_id IN "
                    + "(SELECT id FROM zatca_standard_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_tax_subtotals WHERE header_id IN "
                    + "(SELECT id FROM zatca_standard_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_allowances WHERE header_id IN "
                    + "(SELECT id FROM zatca_standard_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_headers WHERE company_id = ?",
                    companyId);
            jdbcTemplate.update("DELETE FROM zatca_configs WHERE company_id = ?", companyId);
            jdbcTemplate.update("DELETE FROM companies WHERE id = ?", companyId);
        } finally {
            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }
    }

    @Test
    void happyPath_invoiceTypeCode388_returns201AndPersistsHeaderAndChildren() throws Exception {
        String body = buildStandardPayload("ZATCA-STD-001", "388", "10000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", null, true);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("ZATCA-STD-001"))
                .andExpect(jsonPath("$.internalStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.erpReferenceId").value("ERP-ZATCA-STD-001"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> header = jdbcTemplate.queryForMap(
                "SELECT status, invoice_type_code, transaction_type_code, currency, "
                        + "erp_reference_id, created_by, clearance_status, seller_vat_number, "
                        + "buyer_vat_number, zatca_config_id "
                        + "FROM zatca_standard_headers WHERE id = ?::uuid", docId);
        assertEquals("ACCEPTED", header.get("status"));
        assertEquals("388", header.get("invoice_type_code"));
        assertEquals("10000000", header.get("transaction_type_code"));
        assertEquals("SAR", header.get("currency"));
        assertEquals("ERP-ZATCA-STD-001", header.get("erp_reference_id"));
        assertEquals(gatewayPrincipalId, header.get("created_by"));
        assertEquals("CLEARED", header.get("clearance_status"));
        assertEquals("300000000100003", header.get("seller_vat_number"));
        assertEquals("300000000200003", header.get("buyer_vat_number"));
        assertNotNull(header.get("zatca_config_id"));

        int lineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zatca_standard_lines WHERE header_id = ?::uuid",
                Integer.class, docId);
        assertEquals(2, lineCount);

        Map<String, Object> line1 = jdbcTemplate.queryForMap(
                "SELECT item_net_price, item_gross_price, vat_category_code, vat_rate, vat_amount "
                        + "FROM zatca_standard_lines WHERE header_id = ?::uuid AND line_number = 1",
                docId);
        assertEquals(new BigDecimal("120.00"), line1.get("item_net_price"));
        assertEquals(new BigDecimal("120.00"), line1.get("item_gross_price"));
        assertEquals("S", line1.get("vat_category_code"));

        Map<String, Object> line2 = jdbcTemplate.queryForMap(
                "SELECT vat_category_code, exemption_reason_code, exemption_reason_text "
                        + "FROM zatca_standard_lines WHERE header_id = ?::uuid AND line_number = 2",
                docId);
        assertEquals("E", line2.get("vat_category_code"));
        assertEquals("VATEX-SA-29", line2.get("exemption_reason_code"));

        int subtotalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zatca_standard_tax_subtotals WHERE header_id = ?::uuid",
                Integer.class, docId);
        assertEquals(2, subtotalCount);

        Map<String, Object> subS = jdbcTemplate.queryForMap(
                "SELECT vat_category_code, vat_rate, taxable_amount, tax_amount "
                        + "FROM zatca_standard_tax_subtotals "
                        + "WHERE header_id = ?::uuid AND vat_category_code = 'S'",
                docId);
        assertEquals("S", subS.get("vat_category_code"));
        assertEquals(new BigDecimal("600.00"), subS.get("taxable_amount"));
        assertEquals(new BigDecimal("90.00"), subS.get("tax_amount"));

        Map<String, Object> subE = jdbcTemplate.queryForMap(
                "SELECT vat_category_code, taxable_amount, tax_amount, exemption_reason_code "
                        + "FROM zatca_standard_tax_subtotals "
                        + "WHERE header_id = ?::uuid AND vat_category_code = 'E'",
                docId);
        assertEquals("E", subE.get("vat_category_code"));
        assertEquals(new BigDecimal("400.00"), subE.get("taxable_amount"));
        assertEquals(new BigDecimal("0.00"), subE.get("tax_amount"));
        assertEquals("VATEX-SA-29", subE.get("exemption_reason_code"));

        int headerAllowanceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zatca_standard_allowances WHERE header_id = ?::uuid",
                Integer.class, docId);
        assertEquals(1, headerAllowanceCount);

        Map<String, Object> ha = jdbcTemplate.queryForMap(
                "SELECT amount, vat_category_code, reason "
                        + "FROM zatca_standard_allowances WHERE header_id = ?::uuid",
                docId);
        assertEquals(new BigDecimal("50.00"), ha.get("amount"));
        assertEquals("S", ha.get("vat_category_code"));

        assertArchiveRowExistsWithOutcome(201, ENDPOINT);
        assertAuditLogExists("INGESTED");
    }

    @Test
    void transactionTypeCodeStartingWith0_returns400PatternViolation() throws Exception {
        String body = buildStandardPayload("ZATCA-STD-TTC", "388", "00000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", null, false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void exemptionLineMissingReasonFields_returns400() throws Exception {
        String body = buildStandardPayloadExemptionNoReason();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAT_EXEMPTION_REASON_REQUIRED"));
    }

    @Test
    void buyerNull_returns400() throws Exception {
        String body = buildStandardPayloadNoBuyer();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void sellerVatNumberInvalidPattern_returns400() throws Exception {
        String body = buildStandardPayloadBadVat();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void creditNote_knownOriginal_returns201WithOriginalDocumentId() throws Exception {
        String invoiceBody = buildStandardPayload("ZATCA-STD-ORIG", "388", "10000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", null, false);
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody))
                .andExpect(status().isCreated());

        UUID origDocId = jdbcTemplate.queryForObject(
                "SELECT id FROM zatca_standard_headers "
                        + "WHERE invoice_number = 'ZATCA-STD-ORIG'", UUID.class);

        String creditNoteBody = buildStandardPayload("ZATCA-CN-KNOWN", "381", "11000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", "ZATCA-STD-ORIG", false);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creditNoteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("ZATCA-CN-KNOWN"))
                .andReturn().getResponse().getContentAsString();

        String cnDocId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> cnHeader = jdbcTemplate.queryForMap(
                "SELECT original_invoice_id, original_invoice_number "
                        + "FROM zatca_standard_headers WHERE id = ?::uuid", cnDocId);
        assertEquals(origDocId, cnHeader.get("original_invoice_id"));
        assertEquals("ZATCA-STD-ORIG", cnHeader.get("original_invoice_number"));
    }

    @Test
    void creditNote_unknownOriginal_returns201WithNullOriginalDocumentId() throws Exception {
        String body = buildStandardPayload("ZATCA-CN-UNKNOWN", "381", "11000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", "UNKNOWN-XYZ", false);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("ZATCA-CN-UNKNOWN"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> header = jdbcTemplate.queryForMap(
                "SELECT original_invoice_id, original_invoice_number "
                        + "FROM zatca_standard_headers WHERE id = ?::uuid", docId);
        assertNull(header.get("original_invoice_id"));
        assertEquals("UNKNOWN-XYZ", header.get("original_invoice_number"));
    }

    private String buildStandardPayload(String invoiceNumber, String invoiceTypeCode,
            String transactionTypeCode, String companyRegNumber, String environment,
            String status, String originalInvoiceNumber, boolean withHeaderAllowance) {
        String originalField = originalInvoiceNumber != null
                ? ",\"originalInvoiceNumber\":\"" + originalInvoiceNumber + "\""
                : "";
        String headerAllowance = withHeaderAllowance
                ? ",\"allowances\": [{"
                        + "\"sequence\": 1, "
                        + "\"amount\": 50.00, "
                        + "\"baseAmount\": 200.00, "
                        + "\"vatCategoryCode\": \"S\", "
                        + "\"vatRate\": 15.00, "
                        + "\"reasonCode\": \"82\", "
                        + "\"reason\": \"Sample discount\"}]"
                : "";
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "erpReferenceId": "ERP-ZATCA-STD-001",
                  "invoiceNumber": "%s",
                  "invoiceTypeCode": "%s",
                  "transactionTypeCode": "%s",
                  "issueDate": "2026-05-27",
                  "issueTime": "14:30:00",
                  "seller": {
                    "partyId": "seller-001",
                    "partyIdScheme": "CRN",
                    "vatNumber": "300000000100003",
                    "buildingNumber": "1111",
                    "postalCode": "12345",
                    "street": "King Fahd Road",
                    "city": "Riyadh",
                    "countryCode": "SA"
                  },
                  "buyer": {
                    "partyId": "buyer-001",
                    "partyIdScheme": "CRN",
                    "vatNumber": "300000000200003",
                    "buildingNumber": "2222",
                    "postalCode": "54321",
                    "street": "Olaya Street",
                    "city": "Riyadh",
                    "countryCode": "SA"
                  },
                  "currency": "SAR",
                  "lineExtensionAmount": 1000.00,
                  "allowanceTotalAmount": 50.00,
                  "taxExclusiveAmount": 950.00,
                  "taxAmount": 90.00,
                  "taxInclusiveAmount": 1040.00,
                  "prepaidAmount": 0,
                  "payableAmount": 1040.00,
                  "paymentMeansCode": "10",
                  "paymentMeansText": "Cash",
                  "invoiceCounterValue": 42,
                  "previousInvoiceHash": "prevHashABC",
                  "invoiceHash": "hashDEF123",
                  "qrCodeBase64": "QR_BASE64_DATA",
                  "clearanceStatus": "CLEARED"%s%s,
                  "lines": [
                    {
                      "lineNumber": 1,
                      "itemCode": "ITEM-S-001",
                      "description": "Standard-rated service",
                      "unitType": "EA",
                      "quantity": 5.0,
                      "unitPrice": 120.00,
                      "itemGrossPrice": 120.00,
                      "itemPriceDiscount": 0,
                      "itemPriceBaseQuantity": 1,
                      "lineExtensionAmount": 600.00,
                      "netAmount": 600.00,
                      "vatCategoryCode": "S",
                      "vatRate": 15.00,
                      "vatAmount": 90.00
                    },
                    {
                      "lineNumber": 2,
                      "itemCode": "ITEM-E-001",
                      "description": "Exempt service",
                      "unitType": "EA",
                      "quantity": 2.0,
                      "unitPrice": 200.00,
                      "itemGrossPrice": 200.00,
                      "itemPriceDiscount": 0,
                      "itemPriceBaseQuantity": 1,
                      "lineExtensionAmount": 400.00,
                      "netAmount": 400.00,
                      "vatCategoryCode": "E",
                      "vatRate": 0.00,
                      "vatAmount": 0.00,
                      "exemptionReasonCode": "VATEX-SA-29",
                      "exemptionReasonText": "Financial services exempt under Article 29"
                    }
                  ]
                }""".formatted(companyRegNumber, environment, status,
                invoiceNumber, invoiceTypeCode, transactionTypeCode,
                originalField, headerAllowance);
    }

    private String buildStandardPayloadExemptionNoReason() {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "SANDBOX",
                  "status": "VALID",
                  "erpReferenceId": "ERP-ZATCA-STD-001",
                  "invoiceNumber": "ZATCA-STD-EXEMPT-NO-REASON",
                  "invoiceTypeCode": "388",
                  "transactionTypeCode": "10000000",
                  "issueDate": "2026-05-27",
                  "issueTime": "14:30:00",
                  "seller": {
                    "partyId": "seller-001",
                    "vatNumber": "300000000100003",
                    "buildingNumber": "1111",
                    "postalCode": "12345",
                    "city": "Riyadh",
                    "countryCode": "SA"
                  },
                  "buyer": {
                    "partyId": "buyer-001",
                    "vatNumber": "300000000200003",
                    "buildingNumber": "2222",
                    "postalCode": "54321",
                    "city": "Riyadh",
                    "countryCode": "SA"
                  },
                  "currency": "SAR",
                  "lineExtensionAmount": 500.00,
                  "taxExclusiveAmount": 500.00,
                  "taxAmount": 0,
                  "taxInclusiveAmount": 500.00,
                  "payableAmount": 500.00,
                  "lines": [
                    {
                      "lineNumber": 1,
                      "itemCode": "ITEM-E-NO-REASON",
                      "description": "Exempt without reason",
                      "unitType": "EA",
                      "quantity": 1.0,
                      "unitPrice": 500.00,
                      "lineExtensionAmount": 500.00,
                      "netAmount": 500.00,
                      "vatCategoryCode": "E",
                      "vatRate": 0.00,
                      "vatAmount": 0.00
                    }
                  ]
                }""".formatted(TEST_TAX_NUMBER);
    }

    private String buildStandardPayloadNoBuyer() {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "SANDBOX",
                  "status": "VALID",
                  "invoiceNumber": "ZATCA-STD-NO-BUYER",
                  "invoiceTypeCode": "388",
                  "transactionTypeCode": "10000000",
                  "issueDate": "2026-05-27",
                  "issueTime": "14:30:00",
                  "seller": {
                    "partyId": "seller-001",
                    "vatNumber": "300000000100003",
                    "buildingNumber": "1111",
                    "postalCode": "12345",
                    "city": "Riyadh",
                    "countryCode": "SA"
                  },
                  "currency": "SAR",
                  "lineExtensionAmount": 500.00,
                  "taxExclusiveAmount": 500.00,
                  "taxAmount": 75.00,
                  "taxInclusiveAmount": 575.00,
                  "payableAmount": 575.00,
                  "lines": [
                    {
                      "lineNumber": 1,
                      "itemCode": "ITEM-001",
                      "description": "Test item",
                      "unitType": "EA",
                      "quantity": 1.0,
                      "unitPrice": 500.00,
                      "lineExtensionAmount": 500.00,
                      "netAmount": 500.00,
                      "vatCategoryCode": "S",
                      "vatRate": 15.00,
                      "vatAmount": 75.00
                    }
                  ]
                }""".formatted(TEST_TAX_NUMBER);
    }

    private String buildStandardPayloadBadVat() {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "SANDBOX",
                  "status": "VALID",
                  "invoiceNumber": "ZATCA-STD-BAD-VAT",
                  "invoiceTypeCode": "388",
                  "transactionTypeCode": "10000000",
                  "issueDate": "2026-05-27",
                  "issueTime": "14:30:00",
                  "seller": {
                    "partyId": "seller-001",
                    "vatNumber": "12345",
                    "buildingNumber": "1111",
                    "postalCode": "12345",
                    "city": "Riyadh",
                    "countryCode": "SA"
                  },
                  "buyer": {
                    "partyId": "buyer-001",
                    "vatNumber": "300000000200003",
                    "buildingNumber": "2222",
                    "postalCode": "54321",
                    "city": "Riyadh",
                    "countryCode": "SA"
                  },
                  "currency": "SAR",
                  "lineExtensionAmount": 500.00,
                  "taxExclusiveAmount": 500.00,
                  "taxAmount": 75.00,
                  "taxInclusiveAmount": 575.00,
                  "payableAmount": 575.00,
                  "lines": [
                    {
                      "lineNumber": 1,
                      "itemCode": "ITEM-001",
                      "description": "Test item",
                      "unitType": "EA",
                      "quantity": 1.0,
                      "unitPrice": 500.00,
                      "lineExtensionAmount": 500.00,
                      "netAmount": 500.00,
                      "vatCategoryCode": "S",
                      "vatRate": 15.00,
                      "vatAmount": 75.00
                    }
                  ]
                }""".formatted(TEST_TAX_NUMBER);
    }

    private void assertArchiveRowExistsWithOutcome(int expectedOutcome, String endpoint) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_payload_archive "
                        + "WHERE outcome = ? AND endpoint = ?",
                Integer.class, (short) expectedOutcome, endpoint);
        assertNotNull(count);
        assertEquals(1, count);
    }

    private void assertAuditLogExists(String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ? AND company_id = ?",
                Integer.class, action, companyId);
        assertNotNull(count);
        assertEquals(1, count);
    }
}
