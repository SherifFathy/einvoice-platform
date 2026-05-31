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
class ZatcaSimplifiedIngestionIT {

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

    private static final String ENDPOINT = "/api/integration/v1/zatca/simplified";
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
                        .nameEn("ZATCA Simplified IT Co")
                        .nameAr("ZATCA Simplified IT Co AR")
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
            jdbcTemplate.update("DELETE FROM zatca_simplified_line_allowances WHERE line_id IN "
                    + "(SELECT l.id FROM zatca_simplified_lines l JOIN zatca_simplified_headers h "
                    + "ON l.header_id = h.id WHERE h.company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_simplified_lines WHERE header_id IN "
                    + "(SELECT id FROM zatca_simplified_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_simplified_tax_subtotals WHERE header_id IN "
                    + "(SELECT id FROM zatca_simplified_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_simplified_allowances WHERE header_id IN "
                    + "(SELECT id FROM zatca_simplified_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_simplified_headers WHERE company_id = ?",
                    companyId);
            jdbcTemplate.update("DELETE FROM zatca_configs WHERE company_id = ?", companyId);
            jdbcTemplate.update("DELETE FROM companies WHERE id = ?", companyId);
        } finally {
            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }
    }

    @Test
    void happyPath_anonymousBuyer_returns201AndPersistsHeaderWithReportingStatus()
            throws Exception {
        String body = buildSimplifiedPayload("ZATCA-SIMP-001", "388", "00000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", null, false);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("ZATCA-SIMP-001"))
                .andExpect(jsonPath("$.internalStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.erpReferenceId").value("ERP-ZATCA-SIMP-001"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> header = jdbcTemplate.queryForMap(
                "SELECT status, invoice_type_code, transaction_type_code, currency, "
                        + "erp_reference_id, created_by, reporting_status, seller_vat_number, "
                        + "buyer_data, zatca_config_id "
                        + "FROM zatca_simplified_headers WHERE id = ?::uuid", docId);
        assertEquals("ACCEPTED", header.get("status"));
        assertEquals("388", header.get("invoice_type_code"));
        assertEquals("00000000", header.get("transaction_type_code"));
        assertEquals("SAR", header.get("currency"));
        assertEquals("ERP-ZATCA-SIMP-001", header.get("erp_reference_id"));
        assertEquals(gatewayPrincipalId, header.get("created_by"));
        assertEquals("REPORTED", header.get("reporting_status"));
        assertEquals("300000000100003", header.get("seller_vat_number"));
        assertNotNull(header.get("zatca_config_id"));

        int lineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zatca_simplified_lines WHERE header_id = ?::uuid",
                Integer.class, docId);
        assertEquals(1, lineCount);

        Map<String, Object> line1 = jdbcTemplate.queryForMap(
                "SELECT item_net_price, vat_category_code, vat_rate, vat_amount "
                        + "FROM zatca_simplified_lines WHERE header_id = ?::uuid AND line_number = 1",
                docId);
        assertEquals(new BigDecimal("50.00"), line1.get("item_net_price"));
        assertEquals("S", line1.get("vat_category_code"));

        int subtotalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zatca_simplified_tax_subtotals WHERE header_id = ?::uuid",
                Integer.class, docId);
        assertEquals(1, subtotalCount);

        assertArchiveRowExistsWithOutcome(201, ENDPOINT);
        assertAuditLogExists("INGESTED");
    }

    @Test
    void duplicateInvoiceNumber_returns409() throws Exception {
        String body = buildSimplifiedPayload("ZATCA-SIMP-DUP", "388", "00000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", null, false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SIMPLIFIED_NUMBER"));
    }

    @Test
    void transactionTypeCodeStartingWith1_returns400PatternViolation() throws Exception {
        String body = buildSimplifiedPayload("ZATCA-SIMP-TTC", "388", "10000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", null, false);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void creditNote_knownOriginal_returns201WithOriginalDocumentId() throws Exception {
        String invoiceBody = buildSimplifiedPayload("ZATCA-SIMP-ORIG", "388", "00000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", null, false);
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody))
                .andExpect(status().isCreated());

        UUID origDocId = jdbcTemplate.queryForObject(
                "SELECT id FROM zatca_simplified_headers "
                        + "WHERE invoice_number = 'ZATCA-SIMP-ORIG'", UUID.class);

        String creditNoteBody = buildSimplifiedPayload("ZATCA-CN-KNOWN", "381", "01000000",
                TEST_TAX_NUMBER, "SANDBOX", "VALID", "ZATCA-SIMP-ORIG", false);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creditNoteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("ZATCA-CN-KNOWN"))
                .andReturn().getResponse().getContentAsString();

        String cnDocId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> cnHeader = jdbcTemplate.queryForMap(
                "SELECT original_invoice_id, original_invoice_number "
                        + "FROM zatca_simplified_headers WHERE id = ?::uuid", cnDocId);
        assertEquals(origDocId, cnHeader.get("original_invoice_id"));
        assertEquals("ZATCA-SIMP-ORIG", cnHeader.get("original_invoice_number"));
    }

    @Test
    void creditNote_unknownOriginal_returns201WithNullOriginalDocumentId() throws Exception {
        String body = buildSimplifiedPayload("ZATCA-CN-UNKNOWN", "381", "01000000",
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
                        + "FROM zatca_simplified_headers WHERE id = ?::uuid", docId);
        assertNull(header.get("original_invoice_id"));
        assertEquals("UNKNOWN-XYZ", header.get("original_invoice_number"));
    }

    private String buildSimplifiedPayload(String invoiceNumber, String invoiceTypeCode,
            String transactionTypeCode, String companyRegNumber, String environment,
            String status, String originalInvoiceNumber, boolean withBuyer) {
        String originalField = originalInvoiceNumber != null
                ? ",\"originalInvoiceNumber\":\"" + originalInvoiceNumber + "\""
                : "";
        String buyerBlock = withBuyer
                ? ",\"buyer\": {"
                        + "\"partyId\": \"buyer-simp-001\","
                        + "\"vatNumber\": \"300000000200003\","
                        + "\"buildingNumber\": \"2222\","
                        + "\"postalCode\": \"54321\","
                        + "\"street\": \"Olaya Street\","
                        + "\"city\": \"Riyadh\","
                        + "\"countryCode\": \"SA\"}"
                : "";
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "erpReferenceId": "ERP-ZATCA-SIMP-001",
                  "invoiceNumber": "%s",
                  "invoiceTypeCode": "%s",
                  "transactionTypeCode": "%s",
                  "issueDate": "2026-05-27",
                  "issueTime": "15:00:00",
                  "seller": {
                    "partyId": "seller-simp-001",
                    "partyIdScheme": "CRN",
                    "vatNumber": "300000000100003",
                    "buildingNumber": "1111",
                    "postalCode": "12345",
                    "street": "King Fahd Road",
                    "city": "Riyadh",
                    "countryCode": "SA"
                  }%s,
                  "currency": "SAR",
                  "lineExtensionAmount": 500.00,
                  "allowanceTotalAmount": 0,
                  "taxExclusiveAmount": 500.00,
                  "taxAmount": 75.00,
                  "taxInclusiveAmount": 575.00,
                  "prepaidAmount": 0,
                  "payableAmount": 575.00,
                  "paymentMeansCode": "10",
                  "paymentMeansText": "Cash",
                  "invoiceCounterValue": 1,
                  "previousInvoiceHash": "prevHashSimp",
                  "invoiceHash": "hashSimp123",
                  "qrCodeBase64": "QR_SIMP_DATA",
                  "reportingStatus": "REPORTED"%s,
                  "lines": [
                    {
                      "lineNumber": 1,
                      "itemCode": "ITEM-SIMP-001",
                      "description": "Standard-rated retail item",
                      "unitType": "EA",
                      "quantity": 10.0,
                      "unitPrice": 50.00,
                      "itemGrossPrice": 50.00,
                      "itemPriceDiscount": 0,
                      "itemPriceBaseQuantity": 1,
                      "lineExtensionAmount": 500.00,
                      "netAmount": 500.00,
                      "vatCategoryCode": "S",
                      "vatRate": 15.00,
                      "vatAmount": 75.00
                    }
                  ]
                }""".formatted(companyRegNumber, environment, status,
                invoiceNumber, invoiceTypeCode, transactionTypeCode,
                buyerBlock, originalField);
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
