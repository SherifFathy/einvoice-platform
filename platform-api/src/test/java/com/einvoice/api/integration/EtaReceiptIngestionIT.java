package com.einvoice.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.rbac.AuthorityEnvironment;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class EtaReceiptIngestionIT {

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

    private static final String ENDPOINT = "/api/integration/v1/eta/receipts";

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
                com.einvoice.core.domain.company.Company.builder()
                        .nameEn("Integration Test Co")
                        .nameAr("Integration Test Co AR")
                        .taxNumber("100200300")
                        .isActive(true)
                        .build()).getId();

        AuthorityEnvironment ae = authorityEnvironmentRepository.save(
                AuthorityEnvironment.builder()
                        .id((short) 100)
                        .authority("ETA")
                        .environment("PREPROD")
                        .label("ETA Preprod")
                        .isActive(true)
                        .build());
        authorityEnvId = ae.getId();
    }

    @AfterEach
    void tearDown() {
        // Temporarily disables V52 append_only_guard trigger to allow test cleanup
        jdbcTemplate.execute("ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE inbound_payload_archive DISABLE TRIGGER ALL");
        jdbcTemplate.update("DELETE FROM audit_logs WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM inbound_payload_archive");
        jdbcTemplate.execute("ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE inbound_payload_archive ENABLE TRIGGER ALL");
        jdbcTemplate.update("DELETE FROM eta_receipt_line_taxes WHERE line_id IN "
                + "(SELECT l.id FROM eta_receipt_lines l JOIN eta_receipt_headers h "
                + "ON l.header_id = h.id WHERE h.company_id = ?)", companyId);
        jdbcTemplate.update("DELETE FROM eta_receipt_lines WHERE header_id IN "
                + "(SELECT id FROM eta_receipt_headers WHERE company_id = ?)", companyId);
        jdbcTemplate.update("DELETE FROM eta_receipt_headers WHERE company_id = ?", companyId);
        companyRepository.deleteAll();
        authorityEnvironmentRepository.deleteAll();
    }

    @Test
    void happyPath_returns201AndPersistsReceipt() throws Exception {
        String body = buildReceiptPayload("R-2026-001", "100200300",
                "PREPROD", "VALID", null);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("R-2026-001"))
                .andExpect(jsonPath("$.internalStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.erpReferenceId").value("ERP-REF-001"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> header = jdbcTemplate.queryForMap(
                "SELECT state, eta_receipt_uuid, erp_reference_id, created_by, "
                        + "receipt_number, document_type, currency "
                        + "FROM eta_receipt_headers WHERE id = ?::uuid", docId);
        assertEquals("ACCEPTED", header.get("state"));
        assertNotNull(header.get("eta_receipt_uuid"));
        assertEquals("ERP-REF-001", header.get("erp_reference_id"));
        assertEquals(gatewayPrincipalId, header.get("created_by"));

        int lineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eta_receipt_lines WHERE header_id = ?::uuid",
                Integer.class, docId);
        assertEquals(1, lineCount);

        int taxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eta_receipt_line_taxes WHERE line_id IN "
                        + "(SELECT id FROM eta_receipt_lines WHERE header_id = ?::uuid)",
                Integer.class, docId);
        assertEquals(1, taxCount);

        assertArchiveRowExistsWithOutcome(201);
        assertAuditLogExists("INGESTED");
    }

    @Test
    void duplicateReceiptNumber_returns409() throws Exception {
        String body = buildReceiptPayload("R-2026-DUP", "100200300",
                "PREPROD", "VALID", null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RECEIPT_NUMBER"));

        assertArchiveRowCount(2);
    }

    @Test
    void missingReceiptNumber_returns400() throws Exception {
        String body = """
                {
                  "companyRegistrationNumber": "100200300",
                  "environment": "PREPROD",
                  "status": "VALID",
                  "header": {
                    "dateTimeIssued": "2026-05-27T14:30:00Z",
                    "uuid": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
                  },
                  "documentType": {"receiptType": "r", "typeVersion": "1.2"},
                  "seller": {"rin": "100200300", "tradeName": "Test",
                  "deviceSerialNumber": "POS-1", "activityCode": "4610"},
                  "paymentMethod": "C",
                  "totalSales": 100,
                  "netAmount": 100,
                  "totalAmount": 114,
                  "itemData": [{"itemCode": "IC-1", "itemType": "EGS", "description": "Item",
                  "unitType": "EA", "quantity": 1, "unitPrice": 100}]
                }""";

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertArchiveRowExistsWithOutcome(400);
    }

    @Test
    void invalidUuid_returns400() throws Exception {
        String body = buildReceiptPayload("R-2026-UUID", "100200300",
                "PREPROD", "VALID", "not-valid-uuid");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownCompany_returns404() throws Exception {
        String body = buildReceiptPayload("R-2026-UNK", "999999999",
                "PREPROD", "VALID", null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"))
                .andExpect(jsonPath("$.details.registrationNumber").value("999999999"));

        Map<String, Object> archiveRow = jdbcTemplate.queryForMap(
                "SELECT outcome, company_id FROM inbound_payload_archive WHERE outcome = 404");
        assertNull(archiveRow.get("company_id"));
    }

    @Test
    void environmentProduction_returns400() throws Exception {
        String body = """
                {
                  "companyRegistrationNumber": "100200300",
                  "environment": "PRODUCTION",
                  "status": "VALID",
                  "header": {
                    "dateTimeIssued": "2026-05-27T14:30:00Z",
                    "receiptNumber": "R-2026-PROD",
                    "uuid": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
                  },
                  "documentType": {"receiptType": "r", "typeVersion": "1.2"},
                  "seller": {"rin": "100200300", "tradeName": "Test",
                  "deviceSerialNumber": "POS-1", "activityCode": "4610"},
                  "buyer": {"type": "P"},
                  "paymentMethod": "C",
                  "totalSales": 100,
                  "netAmount": 100,
                  "totalAmount": 114,
                  "itemData": [{"itemCode": "IC-1", "itemType": "EGS", "description": "Item",
                  "unitType": "EA", "quantity": 1, "unitPrice": 100}]
                }""";

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void buyerTypeB_missingIdAndName_returns400() throws Exception {
        String body = buildReceiptPayloadWithBuyer("R-2026-BUYB", "100200300",
                "PREPROD", "VALID", "B", null, null, "114.00");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.buyerType").value("B"));
    }

    @Test
    void buyerTypeP_totalAmountGte150000_missingIdAndName_returns400() throws Exception {
        String body = buildReceiptPayloadWithBuyer("R-2026-BUYP", "100200300",
                "PREPROD", "VALID", "P", null, null, "150000.00");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.buyerType").value("P"));
    }

    private String buildReceiptPayload(String receiptNumber, String companyRegNumber,
            String environment, String status, String customUuid) {
        String uuid = customUuid != null ? customUuid
                : "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "erpReferenceId": "ERP-REF-001",
                  "header": {
                    "dateTimeIssued": "2026-05-27T14:30:00Z",
                    "receiptNumber": "%s",
                    "uuid": "%s",
                    "currency": "EGP"
                  },
                  "documentType": {"receiptType": "r", "typeVersion": "1.2"},
                  "seller": {
                    "rin": "100200300",
                    "tradeName": "Test Merchant",
                    "deviceSerialNumber": "POS-001",
                    "activityCode": "4610",
                    "branchAddress": {"country": "EG", "governate": "Cairo"}
                  },
                  "buyer": {"type": "P"},
                  "paymentMethod": "C",
                  "totalSales": 100.00,
                  "totalCommercialDiscount": 0,
                  "extraReceiptDiscountData": [],
                  "totalItemsDiscount": 0,
                  "netAmount": 100.00,
                  "totalAmount": 114.00,
                  "taxTotals": [{"taxType": "T1", "amount": 14.00}],
                  "itemData": [
                    {
                      "itemCode": "EGS-ITEM-001",
                      "itemType": "EGS",
                      "description": "Test Product",
                      "unitType": "EA",
                      "quantity": 2.0,
                      "unitPrice": 50.00,
                      "salesTotal": 100.00,
                      "commercialDiscountData": [],
                      "itemDiscountData": [],
                      "valueDifference": 0,
                      "totalTaxableFees": 0,
                      "netTotal": 100.00,
                      "taxAmount": 14.00,
                      "total": 114.00,
                      "taxableItems": [{"taxType": "T1", "amount": 14.00, "subType": "V009", "rate": 14.0}]
                    }
                  ]
                }""".formatted(companyRegNumber, environment, status, receiptNumber, uuid);
    }

    private String buildReceiptPayloadWithBuyer(String receiptNumber, String companyRegNumber,
            String environment, String status, String buyerType,
            String buyerId, String buyerName, String totalAmount) {
        String uuid = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
        String buyerJson = buyerId != null || buyerName != null
                ? String.format("{\"type\":\"%s\",\"id\":%s,\"name\":%s}", buyerType,
                        buyerId != null ? "\"" + buyerId + "\"" : "null",
                        buyerName != null ? "\"" + buyerName + "\"" : "null")
                : String.format("{\"type\":\"%s\"}", buyerType);
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "header": {
                    "dateTimeIssued": "2026-05-27T14:30:00Z",
                    "receiptNumber": "%s",
                    "uuid": "%s",
                    "currency": "EGP"
                  },
                  "documentType": {"receiptType": "r", "typeVersion": "1.2"},
                  "seller": {
                    "rin": "100200300",
                    "tradeName": "Test Merchant",
                    "deviceSerialNumber": "POS-001",
                    "activityCode": "4610"
                  },
                  "buyer": %s,
                  "paymentMethod": "C",
                  "totalSales": 150000.00,
                  "totalCommercialDiscount": 0,
                  "extraReceiptDiscountData": [],
                  "totalItemsDiscount": 0,
                  "netAmount": %s,
                  "totalAmount": %s,
                  "taxTotals": [{"taxType": "T1", "amount": 14.00}],
                  "itemData": [
                    {
                      "itemCode": "EGS-ITEM-001",
                      "itemType": "EGS",
                      "description": "Test Product",
                      "unitType": "EA",
                      "quantity": 1.0,
                      "unitPrice": %s,
                      "salesTotal": %s,
                      "commercialDiscountData": [],
                      "itemDiscountData": [],
                      "valueDifference": 0,
                      "totalTaxableFees": 0,
                      "netTotal": %s,
                      "taxAmount": 14.00,
                      "total": %s,
                      "taxableItems": [{"taxType": "T1", "amount": 14.00, "subType": "V009", "rate": 14.0}]
                    }
                  ]
                }""".formatted(companyRegNumber, environment, status, receiptNumber, uuid,
                buyerJson, totalAmount, totalAmount, totalAmount, totalAmount, totalAmount, totalAmount);
    }

    private void assertArchiveRowExistsWithOutcome(int expectedOutcome) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_payload_archive WHERE outcome = ?",
                Integer.class, (short) expectedOutcome);
        assertNotNull(count);
        assertEquals(1, count);
    }

    private void assertArchiveRowCount(int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_payload_archive",
                Integer.class);
        assertEquals(expectedCount, count);
    }

    private void assertAuditLogExists(String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ? AND company_id = ?",
                Integer.class, action, companyId);
        assertNotNull(count);
        assertEquals(1, count);
    }
}
