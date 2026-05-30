package com.einvoice.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cross-cutting observability sweep verifying SC-009 / SC-010 invariants
 * across all four ingestion endpoints in a single boot context.
 *
 * <p>FR-OBS-005 fault-injection coverage lives in
 * {@link ObservabilityArchiveFailureIT} because the @MockitoBean approach
 * dirties the bean and would interfere with the other scenarios here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
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

    private static final String TAX_NUMBER = "100200300";

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private AuthorityEnvironmentRepository authorityEnvironmentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private short zatcaEnvId;

    @BeforeEach
    void setUp() {
        companyId = companyRepository.save(Company.builder()
                .nameEn("Observability Co")
                .nameAr("Observability Co AR")
                .taxNumber(TAX_NUMBER)
                .isActive(true)
                .build()).getId();

        zatcaEnvId = authorityEnvironmentRepository
                .findByAuthorityAndEnvironmentAndIsActiveTrue("ZATCA", "SANDBOX")
                .orElseThrow().getId();

        jdbcTemplate.update(
                "INSERT INTO zatca_configs (id, company_id, authority_environment_id, "
                        + "private_key, device_uuid, csr, compliance_certificate, "
                        + "compliance_api_secret) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), companyId.toString(), zatcaEnvId,
                "pk", "uid", "csr", "cert", "secret");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        try {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE company_id = ?", companyId);
            jdbcTemplate.update("DELETE FROM inbound_payload_archive");
            jdbcTemplate.update("DELETE FROM eta_receipt_line_taxes WHERE line_id IN "
                    + "(SELECT l.id FROM eta_receipt_lines l "
                    + "JOIN eta_receipt_headers h ON l.header_id = h.id "
                    + "WHERE h.company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM eta_receipt_lines WHERE header_id IN "
                    + "(SELECT id FROM eta_receipt_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM eta_receipt_headers WHERE company_id = ?", companyId);
            jdbcTemplate.update("DELETE FROM eta_invoice_line_taxes WHERE line_id IN "
                    + "(SELECT l.id FROM eta_invoice_lines l "
                    + "JOIN eta_invoice_headers h ON l.header_id = h.id "
                    + "WHERE h.company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM eta_invoice_lines WHERE header_id IN "
                    + "(SELECT id FROM eta_invoice_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM eta_invoice_headers WHERE company_id = ?", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_line_allowances WHERE line_id IN "
                    + "(SELECT l.id FROM zatca_standard_lines l "
                    + "JOIN zatca_standard_headers h ON l.header_id = h.id "
                    + "WHERE h.company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_lines WHERE header_id IN "
                    + "(SELECT id FROM zatca_standard_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_tax_subtotals WHERE header_id IN "
                    + "(SELECT id FROM zatca_standard_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_allowances WHERE header_id IN "
                    + "(SELECT id FROM zatca_standard_headers WHERE company_id = ?)", companyId);
            jdbcTemplate.update("DELETE FROM zatca_standard_headers WHERE company_id = ?",
                    companyId);
            jdbcTemplate.update("DELETE FROM zatca_simplified_line_allowances WHERE line_id IN "
                    + "(SELECT l.id FROM zatca_simplified_lines l "
                    + "JOIN zatca_simplified_headers h ON l.header_id = h.id "
                    + "WHERE h.company_id = ?)", companyId);
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
    void everyEndpoint_writesOneArchiveRowAndExactlyOneAuditPerHappyPath(CapturedOutput output)
            throws Exception {
        mockMvc.perform(post("/api/integration/v1/eta/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.etaReceipt("OBS-REC-OK", TAX_NUMBER, "PREPROD", "VALID")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/integration/v1/eta/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.etaReceiptMissingNumber(TAX_NUMBER)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/integration/v1/eta/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.etaInvoice("OBS-INV-OK", TAX_NUMBER, "PREPROD", "VALID")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/integration/v1/eta/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.etaInvoiceMissingNumber(TAX_NUMBER)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/integration/v1/zatca/standard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.zatcaStandard("OBS-STD-OK", TAX_NUMBER, "SANDBOX", "VALID")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/integration/v1/zatca/standard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.zatcaStandardBadVat(TAX_NUMBER)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/integration/v1/zatca/simplified")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.zatcaSimplified("OBS-SIMP-OK", TAX_NUMBER, "SANDBOX", "VALID")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/integration/v1/zatca/simplified")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.zatcaSimplifiedBadTtc(TAX_NUMBER)))
                .andExpect(status().isBadRequest());

        Integer archiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_payload_archive", Integer.class);
        assertThat(archiveCount).as("SC-009: one archive row per HTTP request").isEqualTo(8);

        Integer nullOutcomes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_payload_archive WHERE outcome IS NULL",
                Integer.class);
        assertThat(nullOutcomes).as("SC-009: outcome populated after response committed")
                .isEqualTo(0);

        Integer happyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_payload_archive WHERE outcome = 201",
                Integer.class);
        assertThat(happyCount).isEqualTo(4);

        Integer ingestedAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE company_id = ? AND action = 'INGESTED'",
                Integer.class, companyId);
        assertThat(ingestedAuditCount).as("SC-010: one INGESTED audit per happy path").isEqualTo(4);

        List<Map<String, Object>> archiveRows = jdbcTemplate.queryForList(
                "SELECT id FROM inbound_payload_archive");
        for (Map<String, Object> row : archiveRows) {
            String archiveId = row.get("id").toString();
            assertThat(output.getAll())
                    .as("SC-009: log line carries payloadArchiveId %s", archiveId)
                    .contains(archiveId);
        }
    }
}
