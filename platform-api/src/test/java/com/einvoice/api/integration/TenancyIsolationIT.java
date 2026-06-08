package com.einvoice.api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
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
 * Constitution XXIV.6 + FR-009 regression sweep: documents ingested through
 * the gateway under a (company, authority_environment) tuple MUST NOT be
 * visible to read-side sessions scoped to any other tuple.
 *
 * <p>Uses a non-super user with explicit {@code user_company_transaction_roles}
 * assignments scoped to Company A only, so super-user assignment fan-out
 * doesn't mask the cross-company filter under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TenancyIsolationIT {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthorityEnvironmentRepository authorityEnvironmentRepository;
    @Autowired private UserCompanyTransactionRoleRepository uctrRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private Company companyA;
    private Company companyB;
    private String tokenAEtaPreprod;
    private String tokenAZatcaSandbox;

    @BeforeEach
    void setUp() {
        companyA = companyRepository.save(Company.builder()
                .nameEn("Iso Co A").nameAr("Iso Co A AR")
                .taxNumber("ISOA-100").isActive(true).build());
        companyB = companyRepository.save(Company.builder()
                .nameEn("Iso Co B").nameAr("Iso Co B AR")
                .taxNumber("ISOB-200").isActive(true).build());

        authorityEnvironmentRepository.findByAuthorityAndEnvironmentAndIsActiveTrue("ETA", "PREPROD")
                .orElseThrow();
        authorityEnvironmentRepository.findByAuthorityAndEnvironmentAndIsActiveTrue("ZATCA", "SANDBOX")
                .orElseThrow();

        seedZatcaConfig(companyA.getId(), ZATCA_SANDBOX_ID);
        seedZatcaConfig(companyB.getId(), ZATCA_SANDBOX_ID);

        User userA = userRepository.save(User.builder()
                .name("Iso User A").email("isoA@test.com")
                .passwordHash(passwordEncoder.encode("p"))
                .isSuperUser(false).isActive(true).build());

        for (String txType : new String[] {"INVOICE", "RECEIPT", "STANDARD", "SIMPLIFIED"}) {
            uctrRepository.save(UserCompanyTransactionRole.builder()
                    .user(userA).company(companyA)
                    .authorityEnvironmentId(ETA_PREPROD_ID)
                    .transactionType(txType).roleCode("ACCOUNTANT")
                    .isActive(true).build());
            uctrRepository.save(UserCompanyTransactionRole.builder()
                    .user(userA).company(companyA)
                    .authorityEnvironmentId(ZATCA_SANDBOX_ID)
                    .transactionType(txType).roleCode("ACCOUNTANT")
                    .isActive(true).build());
        }

        tokenAEtaPreprod = jwtTokenProvider.createToken(userA.getId(), userA.getEmail(), false,
                "ETA", "PREPROD", ETA_PREPROD_ID, companyA.getId(),
                TenantContext.Mode.OPERATIONAL_MODE);
        tokenAZatcaSandbox = jwtTokenProvider.createToken(userA.getId(), userA.getEmail(), false,
                "ZATCA", "SANDBOX", ZATCA_SANDBOX_ID, companyA.getId(),
                TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        try {
            jdbcTemplate.update("DELETE FROM audit_logs");
            jdbcTemplate.update("DELETE FROM inbound_payload_archive");
            jdbcTemplate.update("DELETE FROM eta_invoice_line_taxes WHERE line_id IN "
                    + "(SELECT id FROM eta_invoice_lines)");
            jdbcTemplate.update("DELETE FROM eta_invoice_lines");
            jdbcTemplate.update("DELETE FROM eta_invoice_headers");
            jdbcTemplate.update("DELETE FROM zatca_standard_line_allowances WHERE line_id IN "
                    + "(SELECT id FROM zatca_standard_lines)");
            jdbcTemplate.update("DELETE FROM zatca_standard_lines");
            jdbcTemplate.update("DELETE FROM zatca_standard_tax_subtotals");
            jdbcTemplate.update("DELETE FROM zatca_standard_allowances");
            jdbcTemplate.update("DELETE FROM zatca_standard_headers");
            jdbcTemplate.update("DELETE FROM zatca_configs");
            uctrRepository.deleteAll();
            userRepository.deleteAll();
            companyRepository.deleteAll();
        } finally {
            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }
    }

    @Test
    void readEndpoints_doNotLeakAcrossCompaniesOrAuthorityEnvironments() throws Exception {
        ingestEtaInvoice("ISO-A-ETA-1", companyA.getTaxNumber());
        ingestEtaInvoice("ISO-B-ETA-1", companyB.getTaxNumber());
        ingestZatcaStandard("ISO-A-ZAT-1", companyA.getTaxNumber());

        // Cross-company isolation under the same authority/environment:
        // user A scoped to Company A + PREPROD must NOT see Company B's invoice.
        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices", companyA.getId())
                        .header("Authorization", "Bearer " + tokenAEtaPreprod))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("ISO-A-ETA-1"));

        // Cross-authority isolation: same user under SANDBOX sees the ZATCA standard.
        mockMvc.perform(get("/api/companies/{companyId}/zatca/standard", companyA.getId())
                        .header("Authorization", "Bearer " + tokenAZatcaSandbox))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("ISO-A-ZAT-1"));

        // Same user under PREPROD (ETA token) trying to reach a ZATCA-only endpoint
        // is rejected by the permission boundary — there is no (authority=ETA,
        // transactionType=STANDARD) role, so cross-authority access never reaches
        // the spec filter. A 403 here is a stricter form of the FR-009 invariant
        // than the env-id filter alone would give.
        mockMvc.perform(get("/api/companies/{companyId}/zatca/standard", companyA.getId())
                        .header("Authorization", "Bearer " + tokenAEtaPreprod))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Cross-company boundary at the controller: accessing Company B's path
        // with user A's token is rejected by verifyContext (401).
        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices", companyB.getId())
                        .header("Authorization", "Bearer " + tokenAEtaPreprod))
                .andExpect(status().isUnauthorized());
    }

    private void ingestEtaInvoice(String invoiceNumber, String taxNumber) throws Exception {
        mockMvc.perform(post("/api/integration/v1/eta/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.etaInvoice(invoiceNumber, taxNumber,
                                "PREPROD", "VALID")))
                .andExpect(status().isCreated());
    }

    private void ingestZatcaStandard(String invoiceNumber, String taxNumber) throws Exception {
        mockMvc.perform(post("/api/integration/v1/zatca/standard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.zatcaStandard(invoiceNumber, taxNumber,
                                "SANDBOX", "VALID")))
                .andExpect(status().isCreated());
    }

    private void seedZatcaConfig(UUID companyId, short authorityEnvironmentId) {
        jdbcTemplate.update(
                "INSERT INTO zatca_configs (id, company_id, authority_environment_id, "
                        + "private_key, device_uuid, csr, compliance_certificate, "
                        + "compliance_api_secret) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), companyId.toString(), authorityEnvironmentId,
                "pk", "uid-" + companyId, "csr", "cert", "secret");
    }
}
