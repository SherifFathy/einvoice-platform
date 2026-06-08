package com.einvoice.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.audit.dto.AuditLogRowDto;
import com.einvoice.api.support.Wave9Fixtures;
import com.einvoice.core.domain.user.User;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@code GET /api/audit-logs} under the company-less
 * {@code AUTHORITY_SCOPED} model (ADR-001), exercising the real query service
 * against a seeded database: audit rows from multiple companies in one
 * authority_environment_id all appear in a single list with the owning company
 * identified (companyId + companyName); the optional companyId filter narrows
 * to one company; rows in another authority_environment_id never appear (env
 * isolation — the hard boundary); and the default order is newest-first. The
 * endpoint is reachable under AUTHORITY_SCOPED with no VIEW gate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditLogScopingIntegrationTest {

    private static final short ETA_PREPROD_ENV = 2;
    private static final short OTHER_ENV = 99;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Wave9Fixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdCompanyIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void listShowsAllCompaniesInEnvWithNamesAndNoViewGate() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        UUID a = env.companyA().getId();
        UUID b = env.companyB().getId();

        fixtures.seedAuditLog(a, ETA_PREPROD_ENV,
                "create", "Company", a.toString());
        fixtures.seedAuditLog(b, ETA_PREPROD_ENV,
                "update", "Company", b.toString());

        String token = authorityScopedToken(env.user());
        AuditLogPage page = list(token);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(AuditLogRowDto::companyId)
                .containsExactlyInAnyOrder(a.toString(), b.toString());
        assertThat(page.items()).allSatisfy(row ->
                assertThat(row.companyName()).isNotBlank());
        page.items().stream()
                .filter(row -> row.companyId().equals(a.toString()))
                .forEach(row -> assertThat(row.companyName())
                        .isEqualTo("Wave9 Co A"));
        page.items().stream()
                .filter(row -> row.companyId().equals(b.toString()))
                .forEach(row -> assertThat(row.companyName())
                        .isEqualTo("Wave9 Co B"));
        assertThat(page.items()).isSortedAccordingTo((x, y) ->
                y.timestamp().compareTo(x.timestamp()));
    }

    @Test
    void companyFilterNarrowsToOneCompany() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        UUID a = env.companyA().getId();
        UUID b = env.companyB().getId();

        fixtures.seedAuditLog(a, ETA_PREPROD_ENV, "create", "Company", "1");
        fixtures.seedAuditLog(a, ETA_PREPROD_ENV, "update", "Company", "2");
        fixtures.seedAuditLog(b, ETA_PREPROD_ENV, "create", "Company", "3");

        AuditLogPage page = list(token(env.user()), "companyId", a.toString());

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).allSatisfy(row ->
                assertThat(row.companyId()).isEqualTo(a.toString()));
    }

    @Test
    void listExcludesEntriesFromOtherAuthorityEnvironment() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        UUID a = env.companyA().getId();

        UUID otherCompany = fixtures
                .createCompany("Other Env Co", "3000000990001").getId();
        createdCompanyIds.add(otherCompany);

        fixtures.seedAuditLog(a, ETA_PREPROD_ENV, "create", "Company", "1");
        fixtures.seedAuditLog(otherCompany, OTHER_ENV,
                "create", "Company", "2");
        fixtures.seedAuditLog(otherCompany, OTHER_ENV,
                "update", "Company", "3");

        AuditLogPage page = list(token(env.user()));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).extracting(AuditLogRowDto::companyId)
                .doesNotContain(otherCompany.toString())
                .contains(a.toString());
    }

    private AuditLogPage list(String token, String... params)
            throws Exception {
        MockHttpServletRequestBuilder req = get("/api/audit-logs")
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        MvcResult result = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andReturn();
        JsonNode root = objectMapper.readTree(
                result.getResponse().getContentAsString());
        List<AuditLogRowDto> items = objectMapper
                .readerForListOf(AuditLogRowDto.class)
                .readValue(root.get("content"));
        return new AuditLogPage(items, root.get("totalElements").asLong());
    }

    private String token(User user) {
        return authorityScopedToken(user);
    }

    private String authorityScopedToken(User user) {
        return jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ETA", "PREPROD", ETA_PREPROD_ENV, null,
                TenantContext.Mode.AUTHORITY_SCOPED);
    }

    private void track(UUID userId, UUID... companyIds) {
        createdUserIds.add(userId);
        createdCompanyIds.addAll(List.of(companyIds));
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
            jdbcTemplate.execute(
                    "ALTER TABLE audit_logs DISABLE TRIGGER ALL");
            deleteByIds("audit_logs", "company_id", companies);
            jdbcTemplate.execute(
                    "ALTER TABLE audit_logs ENABLE TRIGGER ALL");
            deleteByIds("user_company_transaction_roles",
                    "company_id", companies);
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
        String placeholders = ids.stream().map(i -> "?")
                .collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column
                + " IN (" + placeholders + ")", ids.toArray());
    }

    private record AuditLogPage(
            List<AuditLogRowDto> items,
            long totalElements) {
    }
}
