package com.einvoice.api.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.support.Wave9Fixtures;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.user.User;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Wave 9 large-list performance test (SC-008). Seeds 1,000+ submission attempts
 * in one company / authority environment and asserts the unified submission-log
 * query returns a page well under the 2-second budget using the existing
 * compound index on {@code submission_attempts}. The budget is deliberately
 * generous for CI stability; the measured figure is server-side query +
 * serialization via in-process MockMvc (no network), with one warm-up call
 * before timing so JVM/JPA/PostgreSQL planner caches are primed.
 *
 * <p>The submission log is the Wave 9 read endpoint whose row count scales with
 * submission volume, so it is the representative large-list query for this
 * phase. It is reached via the company-less {@code AUTHORITY_SCOPED} scope
 * (ADR-001) and filtered by {@code authority_environment_id} only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentListPerformanceTest {

    private static final short ETA_PREPROD_ENV = 2;
    private static final int ROW_COUNT = 1_200;
    private static final long RESPONSE_BUDGET_MS = 2_000L;

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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private Wave9Fixtures fixtures;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdCompanyIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void submissionLogPageReturnsUnderTwoSecondsForThousandPlusRows() throws Exception {
        User user = fixtures.createRegularUser("wave9-perf@test.com");
        createdUserIds.add(user.getId());
        Company company = fixtures.createCompany("Perf Co", "3000000770001");
        createdCompanyIds.add(company.getId());
        fixtures.grantRole(user.getId(), company.getId(), ETA_PREPROD_ENV,
                "INVOICE", "ACCOUNTANT");
        batchSeedAttempts(company.getId(), ETA_PREPROD_ENV, ROW_COUNT);

        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ETA", "PREPROD", ETA_PREPROD_ENV, null,
                TenantContext.Mode.AUTHORITY_SCOPED);

        getFirstPage(token);
        long start = System.currentTimeMillis();
        JsonNode root = getFirstPage(token);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(root.get("totalElements").asLong())
                .as("all seeded rows are visible within the env")
                .isEqualTo(ROW_COUNT);
        assertThat(root.get("items").size())
                .as("a single page is returned")
                .isGreaterThan(0);
        assertThat(elapsed)
                .as("large-list query returns within the SC-008 budget (ms)")
                .isLessThan(RESPONSE_BUDGET_MS);
    }

    private JsonNode getFirstPage(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/submission-log")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Bulk-inserts the requested number of submission attempts via plain JDBC
     * batch update (far faster than 1,200 entity saves) with monotonically
     * increasing {@code submitted_at} so the {@code submittedAt DESC} sort has
     * real work to do against the compound index.
     *
     * @param companyId owning company
     * @param envId authority environment
     * @param count rows to seed
     */
    private void batchSeedAttempts(UUID companyId, short envId, int count) {
        OffsetDateTime base = OffsetDateTime.now().minusMinutes(count);
        List<Object[]> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            batch.add(new Object[]{
                UUID.randomUUID(), companyId, envId,
                TransactionType.INVOICE.name(), UUID.randomUUID(),
                1, "SUCCESS", base.plusMinutes(i)});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO submission_attempts (id, company_id, "
                        + "authority_environment_id, transaction_type, "
                        + "document_id, attempt_number, result, submitted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                batch);
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
}
