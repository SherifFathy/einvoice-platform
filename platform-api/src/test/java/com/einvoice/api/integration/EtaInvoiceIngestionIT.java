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
class EtaInvoiceIngestionIT {

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

    private static final String ENDPOINT = "/api/integration/v1/eta/invoices";

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
                        .id((short) 101)
                        .authority("ETA")
                        .environment("PREPROD")
                        .label("ETA Preprod")
                        .isActive(true)
                        .build());
        authorityEnvId = ae.getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE inbound_payload_archive DISABLE TRIGGER ALL");
        jdbcTemplate.update("DELETE FROM audit_logs WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM inbound_payload_archive");
        jdbcTemplate.execute("ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE inbound_payload_archive ENABLE TRIGGER ALL");
        jdbcTemplate.update("DELETE FROM eta_invoice_line_taxes WHERE line_id IN "
                + "(SELECT l.id FROM eta_invoice_lines l JOIN eta_invoice_headers h "
                + "ON l.header_id = h.id WHERE h.company_id = ?)", companyId);
        jdbcTemplate.update("DELETE FROM eta_invoice_lines WHERE header_id IN "
                + "(SELECT id FROM eta_invoice_headers WHERE company_id = ?)", companyId);
        jdbcTemplate.update("DELETE FROM eta_invoice_headers WHERE company_id = ?", companyId);
        companyRepository.deleteAll();
        authorityEnvironmentRepository.deleteAll();
    }

    @Test
    void happyPath_invoiceTypeI_returns201AndPersistsInvoice() throws Exception {
        String body = buildInvoicePayload("INV-2026-001", "I", "100200300",
                "PREPROD", "VALID", null, null);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("INV-2026-001"))
                .andExpect(jsonPath("$.internalStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.erpReferenceId").value("ERP-INV-001"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> header = jdbcTemplate.queryForMap(
                "SELECT state, eta_uuid, eta_long_id, eta_submission_id, "
                        + "erp_reference_id, created_by, invoice_number, document_type, currency "
                        + "FROM eta_invoice_headers WHERE id = ?::uuid", docId);
        assertEquals("ACCEPTED", header.get("state"));
        assertEquals("ETA-UUID-001", header.get("eta_uuid"));
        assertEquals("ETA-LONG-ID-001", header.get("eta_long_id"));
        assertEquals("ETA-SUB-ID-001", header.get("eta_submission_id"));
        assertEquals("ERP-INV-001", header.get("erp_reference_id"));
        assertEquals(gatewayPrincipalId, header.get("created_by"));

        int lineCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eta_invoice_lines WHERE header_id = ?::uuid",
                Integer.class, docId);
        assertEquals(1, lineCount);

        int taxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eta_invoice_line_taxes WHERE line_id IN "
                        + "(SELECT id FROM eta_invoice_lines WHERE header_id = ?::uuid)",
                Integer.class, docId);
        assertEquals(1, taxCount);

        assertArchiveRowExistsWithOutcome(201);
        assertAuditLogExists("INGESTED");
    }

    @Test
    void creditNote_knownOriginal_returns201WithOriginalDocumentId() throws Exception {
        String invoiceBody = buildInvoicePayload("INV-2026-ORIG", "I", "100200300",
                "PREPROD", "VALID", null, null);
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody))
                .andExpect(status().isCreated());

        UUID origDocId = jdbcTemplate.queryForObject(
                "SELECT id FROM eta_invoice_headers WHERE invoice_number = 'INV-2026-ORIG'",
                UUID.class);

        String creditNoteBody = buildInvoicePayload("CN-KNOWN", "C", "100200300",
                "PREPROD", "VALID", "INV-2026-ORIG", null);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creditNoteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("CN-KNOWN"))
                .andReturn().getResponse().getContentAsString();

        String cnDocId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> cnHeader = jdbcTemplate.queryForMap(
                "SELECT original_document_id, original_invoice_number FROM eta_invoice_headers "
                        + "WHERE id = ?::uuid", cnDocId);
        assertEquals(origDocId, cnHeader.get("original_document_id"));
        assertEquals("INV-2026-ORIG", cnHeader.get("original_invoice_number"));
    }

    @Test
    void creditNote_missingOriginalInvoiceNumber_returns400() throws Exception {
        String body = buildInvoicePayload("CN-NO-ORIG", "C", "100200300",
                "PREPROD", "VALID", null, null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_ORIGINAL_DOCUMENT"));
    }

    @Test
    void creditNote_unknownOriginal_returns201WithNullOriginalDocumentId() throws Exception {
        String body = buildInvoicePayload("CN-UNKNOWN", "C", "100200300",
                "PREPROD", "VALID", "UNKNOWN-XYZ", null);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value("CN-UNKNOWN"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> header = jdbcTemplate.queryForMap(
                "SELECT original_document_id, original_invoice_number FROM eta_invoice_headers "
                        + "WHERE id = ?::uuid", docId);
        assertNull(header.get("original_document_id"));
        assertEquals("UNKNOWN-XYZ", header.get("original_invoice_number"));
    }

    @Test
    void statusInvalid_rowStateIsRejected() throws Exception {
        String body = buildInvoicePayload("INV-REJECTED", "I", "100200300",
                "PREPROD", "INVALID", null, null);

        String response = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internalStatus").value("REJECTED"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(response).get("id").asText();

        Map<String, Object> header = jdbcTemplate.queryForMap(
                "SELECT state FROM eta_invoice_headers WHERE id = ?::uuid", docId);
        assertEquals("REJECTED", header.get("state"));
    }

    private String buildInvoicePayload(String invoiceNumber, String documentType,
            String companyRegNumber, String environment, String status,
            String originalInvoiceNumber, String customCurrency) {
        String currency = customCurrency != null ? customCurrency : "EGP";
        String originalField = originalInvoiceNumber != null
                ? ",\"originalInvoiceNumber\":\"" + originalInvoiceNumber + "\""
                : "";
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "erpReferenceId": "ERP-INV-001",
                  "invoiceNumber": "%s",
                  "documentType": "%s",
                  "documentTypeVersion": "1.0",
                  "dateTimeIssued": "2026-05-27T14:30:00+02:00",
                  "seller": {
                    "type": "B",
                    "id": "100200300",
                    "name": "Test Seller Corp",
                    "address": {
                      "country": "EG",
                      "governate": "Cairo",
                      "regionCity": "Nasr City",
                      "street": "Abbas El-Akkad",
                      "buildingNumber": "12"
                    }
                  },
                  "buyer": {
                    "type": "B",
                    "id": "300200100",
                    "name": "Test Buyer Corp",
                    "address": {
                      "country": "EG",
                      "governate": "Giza",
                      "regionCity": "Dokki",
                      "street": "Tahrir St",
                      "buildingNumber": "45"
                    }
                  },
                  "currency": "%s",
                  "totalSalesAmount": 1000.00,
                  "totalDiscountAmount": 0,
                  "extraDiscountAmount": 0,
                  "totalItemsDiscountAmount": 0,
                  "netAmount": 1000.00,
                  "totalAmount": 1140.00,
                  "etaUuid": "ETA-UUID-001",
                  "etaLongId": "ETA-LONG-ID-001",
                  "etaSubmissionId": "ETA-SUB-ID-001"%s,
                  "lines": [
                    {
                      "lineNumber": 1,
                      "internalCode": "IC-001",
                      "itemType": "EGS",
                      "itemCode": "EGS-ITEM-001",
                      "description": "Test Service",
                      "unitType": "EA",
                      "quantity": 10.0,
                      "unitValue": {
                        "currencySold": "EGP",
                        "amountEGP": 100.00,
                        "amountSold": 100.00,
                        "currencyExchangeRate": 1.0
                      },
                      "salesTotal": 1000.00,
                      "discountRate": 0,
                      "discountAmount": 0,
                      "itemsDiscount": 0,
                      "valueDifference": 0,
                      "totalTaxableFees": 0,
                      "netTotal": 1000.00,
                      "taxAmount": 140.00,
                      "total": 1140.00,
                      "taxableItems": [
                        {"taxType": "T1", "subType": "V009", "rate": 14.0, "amount": 140.00}
                      ]
                    }
                  ]
                }""".formatted(companyRegNumber, environment, status,
                invoiceNumber, documentType, currency, originalField);
    }

    private void assertArchiveRowExistsWithOutcome(int expectedOutcome) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_payload_archive WHERE outcome = ?",
                Integer.class, (short) expectedOutcome);
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
