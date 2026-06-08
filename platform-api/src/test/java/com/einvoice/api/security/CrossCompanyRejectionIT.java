package com.einvoice.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CrossCompanyRejectionIT {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserCompanyTransactionRoleRepository uctrRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User regularUser;
    private User superUser;
    private Company companyA;
    private Company companyB;

    @BeforeEach
    void setUp() {
        companyA = Company.builder()
                .nameEn("CrossA Co")
                .nameAr("شركة أ")
                .taxNumber("999000000000300")
                .isActive(true)
                .build();
        companyA = companyRepository.save(companyA);

        companyB = Company.builder()
                .nameEn("CrossB Co")
                .nameAr("شركة ب")
                .taxNumber("999000000000301")
                .isActive(true)
                .build();
        companyB = companyRepository.save(companyB);

        regularUser = User.builder()
                .name("Cross Regular")
                .email("cross-regular@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        regularUser = userRepository.save(regularUser);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(regularUser).company(companyA)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE").roleCode("ACCOUNTANT")
                .isActive(true).build());

        superUser = User.builder()
                .name("Cross SU")
                .email("cross-su@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM zatca_standard_headers");
        uctrRepo.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void regularUser_adminEndpoint_rejected403() throws Exception {
        String token = jwtTokenProvider.createToken(
                regularUser.getId(), regularUser.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                companyA.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUser_adminEndpoint_withArbitraryCompanyId_rejected403() throws Exception {
        String token = jwtTokenProvider.createToken(
                regularUser.getId(), regularUser.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                companyA.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/admin/companies/" + companyB.getId() + "/branches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void superUser_adminEndpoint_withAnyCompanyId_allowed() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/admin/companies/" + companyA.getId() + "/branches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void superUser_adminEndpoint_allowed() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void authorityScopedUser_readEndpoint_crossCompanySucceeds() throws Exception {
        String token = jwtTokenProvider.createToken(
                regularUser.getId(), regularUser.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.AUTHORITY_SCOPED);

        mockMvc.perform(get("/api/companies/" + companyA.getId() + "/eta/invoices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void authorityScopedUser_writeEndpoint_crossCompanyWithoutPermission_rejected403() throws Exception {
        String token = jwtTokenProvider.createToken(
                regularUser.getId(), regularUser.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.AUTHORITY_SCOPED);

        mockMvc.perform(post("/api/companies/" + companyB.getId() + "/eta/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorityScopedUser_writeToOtherCompanysDocument_viaPermittedPath_rejected()
            throws Exception {
        // A DRAFT document owned by company B (authEnv = ZATCA SANDBOX = 5).
        UUID docId = insertStandardDraft(companyB.getId(), (short) 5, "STD-IDOR-1");

        // Super user bypasses the permission check, but the path company (A) is still
        // stamped into the tenant context, so the company-scoped loadWithinTenant must
        // refuse to act on a document that belongs to company B.
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5,
                null, TenantContext.Mode.AUTHORITY_SCOPED);

        // IDOR attempt: delete B's document through company A's path -> not found.
        mockMvc.perform(delete("/api/companies/" + companyA.getId()
                        + "/zatca/standard/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // Sanity: the same document IS reachable through its own company's path.
        mockMvc.perform(delete("/api/companies/" + companyB.getId()
                        + "/zatca/standard/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private UUID insertStandardDraft(UUID compId, short envId,
            String invoiceNumber) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO zatca_standard_headers (id, company_id, "
                        + "authority_environment_id, invoice_number, "
                        + "invoice_type_code, transaction_type_code, "
                        + "issue_date, issue_time, seller_data, buyer_data, "
                        + "currency, status, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, '388', '0100000', ?, ?, "
                        + "'{\"partyName\":\"Seller\"}'::jsonb, "
                        + "'{\"partyName\":\"Buyer\"}'::jsonb, "
                        + "'SAR', 'DRAFT', 0, NOW(), NOW())",
                id, compId, envId, invoiceNumber,
                java.time.LocalDate.of(2026, 5, 19),
                java.time.LocalTime.of(12, 0));
        return id;
    }
}
