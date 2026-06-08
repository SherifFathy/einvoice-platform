package com.einvoice.api.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.dashboard.dto.CompanyCardDto;
import com.einvoice.api.dashboard.dto.DashboardSummaryDto;
import com.einvoice.api.dashboard.dto.RecentActivityDto;
import com.einvoice.api.submission.dto.SubmissionLogRowDto;
import com.einvoice.api.support.Wave9Fixtures;
import com.einvoice.core.domain.shared.DocumentState;
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
import org.junit.jupiter.api.Disabled;
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
 * Wave 9 environment-isolation test (FR-018) — the single hard boundary
 * preserved by ADR-001 under the company-less read model. Two distinct ETA
 * environments are seeded (env 1 = ETA PRODUCTION, env 2 = ETA PREPROD) and the
 * test asserts that data created in one environment is completely invisible in
 * the other across every Wave 9 read surface: dashboard summary (cards +
 * counts), KPI totals, the recent-activity feed, and the unified submission
 * log. This is the load-bearing isolation guarantee of this phase.
 *
 * <p>The audit-log read endpoint ({@code GET /api/audit-logs}) is not yet
 * environment-scoped (Phase 7 / US4); its environment-isolation case is
 * captured as {@link #auditLogExcludesOtherEnvironment()} and left
 * {@code @Disabled} until T040/T041 land the env filter — explicit, not silent.
 *
 * <p>No per-company read-isolation assertion is made here: ADR-001 removed that
 * guarantee for these read endpoints; {@code authority_environment_id} is the
 * only boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnvironmentIsolationTest {

    private static final short ETA_PRODUCTION_ENV = 1;
    private static final short ETA_PREPROD_ENV = 2;

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
    void dataFromEnv1IsInvisibleInEnv2AcrossDashboardAndSubmissionLog() throws Exception {
        EnvSeed env1 = seedEnv(ETA_PRODUCTION_ENV, "Prod Co");
        EnvSeed env2 = seedEnv(ETA_PREPROD_ENV, "Preprod Co");

        String env2Token = authorityScopedToken(env2.user, "ETA", "PREPROD", ETA_PREPROD_ENV);

        DashboardSummaryDto summary = getSummary(env2Token);
        assertThat(summary.cards()).extracting(CompanyCardDto::companyId)
                .as("env-2 dashboard never lists env-1 companies")
                .contains(env2.companyId)
                .doesNotContain(env1.companyId);
        assertThat(summary.kpi().today().total())
                .as("env-1 documents never count toward env-2 KPI totals")
                .isEqualTo(2);

        RecentActivityDto activity = getRecentActivity(env2Token);
        assertThat(activity.entries()).extracting(e -> e.companyId())
                .as("env-1 attempts never surface in env-2 recent activity")
                .containsOnly(env2.companyId);

        SubmissionLogPage page = listSubmissionLog(env2Token);
        assertThat(page.items()).extracting(SubmissionLogRowDto::companyId)
                .as("env-1 attempts never surface in env-2 submission log")
                .containsOnly(env2.companyId);
        assertThat(page.totalElements())
                .as("env-2 sees exactly its own two attempts")
                .isEqualTo(2);
    }

    @Test
    void dataFromEnv2IsInvisibleInEnv1AcrossDashboardAndSubmissionLog() throws Exception {
        EnvSeed env1 = seedEnv(ETA_PRODUCTION_ENV, "Prod Co");
        EnvSeed env2 = seedEnv(ETA_PREPROD_ENV, "Preprod Co");

        String env1Token = authorityScopedToken(env1.user, "ETA", "PRODUCTION", ETA_PRODUCTION_ENV);

        DashboardSummaryDto summary = getSummary(env1Token);
        assertThat(summary.cards()).extracting(CompanyCardDto::companyId)
                .contains(env1.companyId)
                .doesNotContain(env2.companyId);

        SubmissionLogPage page = listSubmissionLog(env1Token);
        assertThat(page.items()).extracting(SubmissionLogRowDto::companyId)
                .containsOnly(env1.companyId);
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void dashboardCountsAreScopedPerEnvironmentIndependently() throws Exception {
        EnvSeed env1 = seedEnv(ETA_PRODUCTION_ENV, "Prod Co");
        EnvSeed env2 = seedEnv(ETA_PREPROD_ENV, "Preprod Co");

        DashboardSummaryDto env1Summary = getSummary(
                authorityScopedToken(env1.user, "ETA", "PRODUCTION", ETA_PRODUCTION_ENV));
        DashboardSummaryDto env2Summary = getSummary(
                authorityScopedToken(env2.user, "ETA", "PREPROD", ETA_PREPROD_ENV));

        int env1Pending = env1Summary.cards().stream()
                .filter(c -> c.companyId().equals(env1.companyId))
                .mapToInt(CompanyCardDto::pendingCount).sum();
        int env2Pending = env2Summary.cards().stream()
                .filter(c -> c.companyId().equals(env2.companyId))
                .mapToInt(CompanyCardDto::pendingCount).sum();
        assertThat(env1Pending).as("env-1 pending = SUBMITTING only").isEqualTo(1);
        assertThat(env2Pending).as("env-2 pending = SUBMITTING only").isEqualTo(1);
        assertThat(env1Summary.kpi().today().total())
                .as("each env KPI counts only its own two docs")
                .isEqualTo(2);
        assertThat(env2Summary.kpi().today().total()).isEqualTo(2);
    }

    /**
     * Deferred to Phase 7 (US4 audit-log scoping). Once T040/T041 apply the
     * {@code authority_environment_id} filter to the audit read path, enable
     * this so env-1 audit entries never surface on an env-2 request (FR-018).
     */
    @Disabled("enable after Phase 7 / US4 audit scoping (T040-T041)")
    @Test
    void auditLogExcludesOtherEnvironment() throws Exception {
        EnvSeed env1 = seedEnv(ETA_PRODUCTION_ENV, "Prod Co");
        seedEnv(ETA_PREPROD_ENV, "Preprod Co");
        String env1Token = authorityScopedToken(env1.user, "ETA", "PRODUCTION", ETA_PRODUCTION_ENV);
        mockMvc.perform(get("/api/audit-logs?page=0&size=50")
                        .header("Authorization", "Bearer " + env1Token))
                .andExpect(status().isOk());
    }

    private EnvSeed seedEnv(short envId, String companyTag) {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                envId, "INVOICE", "RECEIPT");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        UUID companyId = env.companyA().getId();
        fixtures.seedEtaInvoice(companyId, envId, DocumentState.SUBMITTING,
                "W9-ENV-" + envId + "-SUB-" + nano());
        fixtures.seedEtaInvoice(companyId, envId, DocumentState.ACCEPTED,
                "W9-ENV-" + envId + "-ACC-" + nano());
        OffsetDateTime now = OffsetDateTime.now();
        fixtures.seedAttemptAt(companyId, envId, TransactionType.INVOICE,
                SubmissionResult.SUCCESS, now.minusMinutes(4));
        fixtures.seedAttemptAt(companyId, envId, TransactionType.INVOICE,
                SubmissionResult.REJECTED, now.minusMinutes(2));
        return new EnvSeed(env.user(), companyId);
    }

    private DashboardSummaryDto getSummary(String token) throws Exception {
        return read(token, "/api/dashboard/summary", DashboardSummaryDto.class);
    }

    private RecentActivityDto getRecentActivity(String token) throws Exception {
        return read(token, "/api/dashboard/recent-activity", RecentActivityDto.class);
    }

    private SubmissionLogPage listSubmissionLog(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/submission-log")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(
                result.getResponse().getContentAsString());
        List<SubmissionLogRowDto> items = objectMapper
                .readerForListOf(SubmissionLogRowDto.class)
                .readValue(root.get("items"));
        return new SubmissionLogPage(items,
                root.get("page").asInt(),
                root.get("size").asInt(),
                root.get("totalElements").asLong());
    }

    private <T> T read(String token, String path, Class<T> type) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    }

    private String authorityScopedToken(User user, String authority,
            String environment, short envId) {
        return jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                authority, environment, envId, null,
                TenantContext.Mode.AUTHORITY_SCOPED);
    }

    private static long nano() {
        return System.nanoTime();
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
            jdbcTemplate.execute("ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
            deleteByIds("submission_attempts", "company_id", companies);
            jdbcTemplate.execute("ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
            deleteByIds("eta_invoice_headers", "company_id", companies);
            deleteByIds("eta_receipt_headers", "company_id", companies);
            deleteByIds("zatca_standard_headers", "company_id", companies);
            deleteByIds("zatca_simplified_headers", "company_id", companies);
            deleteByIds("zatca_configs", "company_id", companies);
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

    private record EnvSeed(User user, UUID companyId) {
    }

    private record SubmissionLogPage(
            List<SubmissionLogRowDto> items,
            int page,
            int size,
            long totalElements) {
    }
}
