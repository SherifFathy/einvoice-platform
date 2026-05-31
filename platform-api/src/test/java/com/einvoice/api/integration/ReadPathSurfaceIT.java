package com.einvoice.api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SC-007 / FR-023 read-path surface check: every document ingested via the
 * gateway is visible through the corresponding internal read endpoint when
 * the caller has a permission-granted Operational Mode session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReadPathSurfaceIT {

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

    private static final short ETA_PREPROD_ID = 2;
    private static final short ZATCA_SANDBOX_ID = 5;
    private static final String TAX_NUMBER = "RPS-100";

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthorityEnvironmentRepository authorityEnvironmentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private UUID companyId;
    private String etaPreprodToken;
    private String zatcaSandboxToken;

    @BeforeEach
    void setUp() {
        companyId = companyRepository.save(Company.builder()
                .nameEn("Read Path Co").nameAr("Read Path Co AR")
                .taxNumber(TAX_NUMBER).isActive(true).build()).getId();

        authorityEnvironmentRepository.findByAuthorityAndEnvironmentAndIsActiveTrue("ETA", "PREPROD")
                .orElseThrow();
        authorityEnvironmentRepository.findByAuthorityAndEnvironmentAndIsActiveTrue("ZATCA", "SANDBOX")
                .orElseThrow();

        jdbcTemplate.update(
                "INSERT INTO zatca_configs (id, company_id, authority_environment_id, "
                        + "private_key, device_uuid, csr, compliance_certificate, "
                        + "compliance_api_secret) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), companyId.toString(), ZATCA_SANDBOX_ID,
                "pk", "uid", "csr", "cert", "secret");

        User user = userRepository.save(User.builder()
                .name("Read Path User").email("readpath@test.com")
                .passwordHash(passwordEncoder.encode("p"))
                .isSuperUser(true).isActive(true).build());

        etaPreprodToken = jwtTokenProvider.createToken(user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", ETA_PREPROD_ID, companyId,
                TenantContext.Mode.OPERATIONAL_MODE);
        zatcaSandboxToken = jwtTokenProvider.createToken(user.getId(), user.getEmail(), true,
                "ZATCA", "SANDBOX", ZATCA_SANDBOX_ID, companyId,
                TenantContext.Mode.OPERATIONAL_MODE);
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
            userRepository.deleteAll();
            companyRepository.deleteAll();
        } finally {
            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }
    }

    @Test
    void allFourIngestedDocuments_appearInTheirReadEndpoints() throws Exception {
        mockMvc.perform(post("/api/integration/v1/eta/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.etaReceipt("RPS-REC-1", TAX_NUMBER, "PREPROD", "VALID")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/integration/v1/eta/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.etaInvoice("RPS-INV-1", TAX_NUMBER, "PREPROD", "VALID")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/integration/v1/zatca/standard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.zatcaStandard("RPS-STD-1", TAX_NUMBER, "SANDBOX", "VALID")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/integration/v1/zatca/simplified")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.zatcaSimplified("RPS-SIMP-1", TAX_NUMBER, "SANDBOX", "VALID")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/receipts", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].receiptNumber").value("RPS-REC-1"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("RPS-INV-1"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/standard", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("RPS-STD-1"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/simplified", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("RPS-SIMP-1"));
    }
}
