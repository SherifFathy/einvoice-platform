package com.einvoice.api.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.submission.dto.SubmissionLogRowDto;
import com.einvoice.api.support.Wave9Fixtures;
import com.einvoice.core.domain.shared.SubmissionResult;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@code GET /api/submission-log} under the company-less
 * {@code AUTHORITY_SCOPED} model (ADR-001), exercising the real query service
 * against a seeded database: all four document classes appear in one list with
 * their owning company identified, the optional company filter narrows to one
 * company, the outcome/transaction-type/date filters work (including
 * {@code IN_FLIGHT}), rows default to newest-first, and attempts in another
 * {@code authority_environment_id} never appear (the hard isolation boundary).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SubmissionLogIntegrationTest {

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
    void listShowsAllFourClassesAcrossCompaniesWithNames() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE", "RECEIPT", "STANDARD", "SIMPLIFIED");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        String token = authorityScopedToken(env.user());

        OffsetDateTime now = OffsetDateTime.now();
        UUID a = env.companyA().getId();
        UUID b = env.companyB().getId();
        fixtures.seedAttemptAt(a, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.SUCCESS, now.minusMinutes(40));
        fixtures.seedAttemptAt(a, ETA_PREPROD_ENV, TransactionType.RECEIPT,
                SubmissionResult.REJECTED, now.minusMinutes(30));
        fixtures.seedAttemptAt(a, ETA_PREPROD_ENV, TransactionType.STANDARD,
                null, now.minusMinutes(20));
        fixtures.seedAttemptAt(a, ETA_PREPROD_ENV, TransactionType.SIMPLIFIED,
                SubmissionResult.ERROR, now.minusMinutes(10));
        fixtures.seedAttemptAt(b, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.SUCCESS, now.minusMinutes(25));
        fixtures.seedAttemptAt(b, ETA_PREPROD_ENV, TransactionType.STANDARD,
                SubmissionResult.TIMEOUT, now.minusMinutes(5));

        SubmissionLogPage page = list(token);

        assertThat(page.items()).hasSize(6);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.items()).extracting(SubmissionLogRowDto::transactionType)
                .containsExactlyInAnyOrder(
                        "INVOICE", "RECEIPT", "STANDARD", "SIMPLIFIED",
                        "INVOICE", "STANDARD");
        assertThat(page.items()).extracting(SubmissionLogRowDto::companyId)
                .containsOnly(a, b);
        assertThat(page.items()).extracting(SubmissionLogRowDto::companyName)
                .allSatisfy(name -> assertThat(name).isNotBlank());
        page.items().stream()
                .filter(row -> row.companyId().equals(a))
                .forEach(row -> assertThat(row.companyName())
                        .isEqualTo("Wave9 Co A"));
        page.items().stream()
                .filter(row -> row.companyId().equals(b))
                .forEach(row -> assertThat(row.companyName())
                        .isEqualTo("Wave9 Co B"));
        assertThat(page.items()).extracting(SubmissionLogRowDto::outcome)
                .contains("IN_FLIGHT");
        assertThat(page.items()).isSortedAccordingTo((x, y) ->
                y.submittedAt().compareTo(x.submittedAt()));
    }

    @Test
    void listCompanyFilterNarrowsToOneCompany() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE", "RECEIPT");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        String token = authorityScopedToken(env.user());
        UUID a = env.companyA().getId();
        UUID b = env.companyB().getId();

        fixtures.seedAttempt(a, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.SUCCESS);
        fixtures.seedAttempt(a, ETA_PREPROD_ENV, TransactionType.RECEIPT,
                SubmissionResult.SUCCESS);
        fixtures.seedAttempt(b, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.SUCCESS);

        SubmissionLogPage page = list(token, "companyId", a.toString());

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).allSatisfy(row ->
                assertThat(row.companyId()).isEqualTo(a));
    }

    @Test
    void listTransactionTypeAndOutcomeFiltersWork() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE", "RECEIPT", "STANDARD", "SIMPLIFIED");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        String token = authorityScopedToken(env.user());
        UUID a = env.companyA().getId();

        fixtures.seedAttempt(a, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.ERROR);
        fixtures.seedAttempt(a, ETA_PREPROD_ENV, TransactionType.SIMPLIFIED,
                null);
        fixtures.seedAttempt(a, ETA_PREPROD_ENV, TransactionType.STANDARD,
                SubmissionResult.SUCCESS);

        SubmissionLogPage simplified = list(token,
                "transactionType", "SIMPLIFIED");
        assertThat(simplified.items()).hasSize(1);
        assertThat(simplified.items().get(0).transactionType())
                .isEqualTo("SIMPLIFIED");

        SubmissionLogPage inFlight = list(token, "outcome", "IN_FLIGHT");
        assertThat(inFlight.items()).hasSize(1);
        assertThat(inFlight.items().get(0).outcome()).isEqualTo("IN_FLIGHT");

        SubmissionLogPage errors = list(token, "outcome", "ERROR");
        assertThat(errors.items()).hasSize(1);
        assertThat(errors.items().get(0).outcome()).isEqualTo("ERROR");
    }

    @Test
    void listExcludesAttemptsFromOtherAuthorityEnvironment() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        UUID a = env.companyA().getId();

        fixtures.seedAttempt(a, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.SUCCESS);

        UUID otherCompany = fixtures
                .createCompany("Other Env Co", "3000000990001").getId();
        createdCompanyIds.add(otherCompany);
        OffsetDateTime now = OffsetDateTime.now();
        fixtures.seedAttemptAt(otherCompany, OTHER_ENV,
                TransactionType.INVOICE, SubmissionResult.SUCCESS,
                now.minusMinutes(1));

        String token = authorityScopedToken(env.user());
        SubmissionLogPage page = list(token);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).extracting(SubmissionLogRowDto::companyId)
                .doesNotContain(otherCompany)
                .contains(a);
    }

    private SubmissionLogPage list(String token, String... params)
            throws Exception {
        MockHttpServletRequestBuilder req = get("/api/submission-log")
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        MvcResult result = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andReturn();
        JsonNode root = objectMapper.readTree(
                result.getResponse().getContentAsString());
        List<SubmissionLogRowDto> items = objectMapper
                .readerForListOf(SubmissionLogRowDto.class)
                .readValue(root.get("items"));
        return new SubmissionLogPage(
                items,
                root.get("page").asInt(),
                root.get("size").asInt(),
                root.get("totalElements").asLong());
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
                    "ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
            deleteByIds("submission_attempts", "company_id", companies);
            jdbcTemplate.execute(
                    "ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
            deleteByIds("eta_invoice_headers", "company_id", companies);
            deleteByIds("eta_receipt_headers", "company_id", companies);
            deleteByIds("zatca_standard_headers", "company_id", companies);
            deleteByIds("zatca_simplified_headers", "company_id", companies);
            deleteByIds("zatca_configs", "company_id", companies);
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

    private record SubmissionLogPage(
            List<SubmissionLogRowDto> items,
            int page,
            int size,
            long totalElements) {
    }
}
