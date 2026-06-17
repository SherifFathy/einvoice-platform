package com.einvoice.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.support.Wave9Fixtures;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the Admin-Mode environment dashboard (US2): exercises the
 * real {@code GET /api/admin/stats} against a seeded database, verifies the
 * identity-only Admin-Mode card source ({@code GET /api/admin/companies}), and
 * asserts hard {@code authority_environment_id} isolation on the env-scoped
 * counts. Counts only — no operational document content (Constitution VII.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminStatsIntegrationTest {

    private static final short ZATCA_SANDBOX_ENV = 5;
    /** ZATCA Simulation — a different, real authority environment (V37). */
    private static final short OTHER_ENV = 4;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Wave9Fixtures fixtures;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdCompanyIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void statsReportEnvScopedCountsMatchingSeeds() throws Exception {
        User superUser = createSuperUser();

        Company coA = createCompany("Stats Co A");
        Company coB = createCompany("Stats Co B");
        grantRole(superUser, coA, ZATCA_SANDBOX_ENV, "STANDARD");
        grantRole(superUser, coB, ZATCA_SANDBOX_ENV, "SIMPLIFIED");

        OffsetDateTime now = OffsetDateTime.now();
        seedAttemptAt(coA.id, ZATCA_SANDBOX_ENV, now);
        seedAttemptAt(coB.id, ZATCA_SANDBOX_ENV, now.minusMinutes(1));
        seedAttemptAt(coA.id, ZATCA_SANDBOX_ENV, now.minusDays(2));

        String token = adminToken(superUser, ZATCA_SANDBOX_ENV);
        JsonNode stats = getStats(token);

        assertThat(stats.get("authorityEnvironmentId").asInt()).isEqualTo(ZATCA_SANDBOX_ENV);
        assertThat(stats.get("totalCompanies").asLong())
                .as("two active UCTR companies in env")
                .isEqualTo(2L);
        assertThat(stats.get("totalUsers").asLong())
                .as("at least the seeded active super user")
                .isGreaterThanOrEqualTo(1L);
        assertThat(stats.get("totalSubmissionsToday").asLong())
                .as("only the two attempts submitted today (UTC) count")
                .isEqualTo(2L);
    }

    @Test
    void envIsolationHoldsForEnvScopedCounts() throws Exception {
        User superUser = createSuperUser();

        Company envCompany = createCompany("Env Company");
        Company otherEnvCompany = createCompany("Other Env Company");
        grantRole(superUser, envCompany, ZATCA_SANDBOX_ENV, "STANDARD");
        grantRole(superUser, otherEnvCompany, OTHER_ENV, "STANDARD");

        OffsetDateTime now = OffsetDateTime.now();
        seedAttemptAt(envCompany.id, ZATCA_SANDBOX_ENV, now);
        seedAttemptAt(otherEnvCompany.id, OTHER_ENV, now);

        String token = adminToken(superUser, ZATCA_SANDBOX_ENV);
        JsonNode stats = getStats(token);

        assertThat(stats.get("totalCompanies").asLong())
                .as("other-env UCTR company is not counted")
                .isEqualTo(1L);
        assertThat(stats.get("totalSubmissionsToday").asLong())
                .as("other-env submission is not counted")
                .isEqualTo(1L);
    }

    @Test
    void adminCompaniesReturnsIdentityCardsWithNoOperationalFigures() throws Exception {
        User superUser = createSuperUser();
        final String token = adminToken(superUser, ZATCA_SANDBOX_ENV);

        Company seeded = createCompany("Card Source Co");
        grantRole(superUser, seeded, ZATCA_SANDBOX_ENV, "STANDARD");

        JsonNode companies = getJson(token, "/api/admin/companies");

        assertThat(companies.isArray()).isTrue();
        JsonNode card = findCard(companies, seeded.id);
        assertThat(card).as("seeded env company appears in the admin card source").isNotNull();
        assertThat(card.has("pendingCount"))
                .as("Admin-Mode cards carry no operational figures (VII.3)").isFalse();
        assertThat(card.has("failedCount")).isFalse();
        assertThat(card.has("certificate")).isFalse();
        assertThat(card.has("kpi")).isFalse();
        assertThat(card.has("entries")).isFalse();
        assertThat(card.get("nameEn").asText()).isEqualTo("Card Source Co");
        assertThat(card.get("taxNumber").asText()).isEqualTo(seeded.taxNumber);
    }

    @Test
    void statsRejectsNonSuperUser() throws Exception {
        User regular = fixtures.createRegularUser("admin-stats-regular@test.com");
        createdUserIds.add(regular.getId());
        String token = jwtTokenProvider.createToken(
                regular.getId(), regular.getEmail(), false,
                "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV, null,
                TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private JsonNode getStats(String token) throws Exception {
        return getJson(token, "/api/admin/stats");
    }

    private JsonNode getJson(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String adminToken(User user, short envId) {
        return jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ZATCA", "SANDBOX", envId, null, TenantContext.Mode.ADMIN_MODE);
    }

    private User createSuperUser() {
        User saved = userRepository.save(User.builder()
                .name("Admin Stats Super")
                .email("admin-stats-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build());
        createdUserIds.add(saved.getId());
        return saved;
    }

    private Company createCompany(String nameEn) {
        var saved = companyRepository.save(com.einvoice.core.domain.company.Company.builder()
                .nameEn(nameEn)
                .nameAr("شركة")
                .taxNumber("300" + UUID.randomUUID().toString().replace("-", "").substring(0, 9))
                .isActive(true)
                .build());
        createdCompanyIds.add(saved.getId());
        return new Company(saved.getId(), saved.getNameEn(), saved.getTaxNumber());
    }

    private void grantRole(User user, Company company, short envId, String txType) {
        fixtures.grantRole(user.getId(), company.id, envId, txType, "ACCOUNTANT");
    }

    private void seedAttemptAt(UUID companyId, short envId, OffsetDateTime when) {
        fixtures.seedAttemptAt(companyId, envId, TransactionType.STANDARD,
                SubmissionResult.SUCCESS, when);
    }

    private static JsonNode findCard(JsonNode companies, UUID companyId) {
        for (JsonNode node : companies) {
            if (companyId.toString().equals(node.get("id").asText())) {
                return node;
            }
        }
        return null;
    }

    private void cleanup() {
        Collection<UUID> companies = new ArrayList<>(createdCompanyIds);
        Collection<UUID> users = new ArrayList<>(createdUserIds);
        createdCompanyIds.clear();
        createdUserIds.clear();
        if (companies.isEmpty() && users.isEmpty()) {
            return;
        }
        if (!companies.isEmpty()) {
            jdbcTemplate.execute("ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
            deleteByIds("submission_attempts", "company_id", companies);
            jdbcTemplate.execute("ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
            deleteByIds("user_company_transaction_roles", "company_id", companies);
            deleteByIds("companies", "id", companies);
        }
        if (!users.isEmpty()) {
            deleteByIds("users", "id", users);
        }
    }

    private void deleteByIds(String table, String column, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column
                + " IN (" + placeholders + ")", ids.toArray());
    }

    private record Company(UUID id, String nameEn, String taxNumber) {
    }
}
