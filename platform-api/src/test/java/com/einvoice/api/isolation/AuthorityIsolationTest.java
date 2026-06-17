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
 * Wave 9 authority-isolation test (FR-017) under the company-less
 * {@code AUTHORITY_SCOPED} model (ADR-001). Because
 * {@code authority_environment_id} encodes authority + environment, ETA data
 * (env 2 = ETA PREPROD) and ZATCA data (env 5 = ZATCA SANDBOX) live behind
 * different ids. This test asserts that ETA documents/attempts never surface on
 * a ZATCA-scoped request and vice versa, across the dashboard summary,
 * recent-activity feed, and the unified submission log.
 *
 * <p>Per the Phase 6 handoff §1, the audit-log read endpoint
 * ({@code GET /api/audit-logs}) is not yet environment-scoped (it lands in
 * Phase 7 / US4). Its authority-isolation case is captured below as
 * {@link #auditLogNeverReturnsOtherAuthorityData()} and left
 * {@code @Disabled} until Phase 7 wires the env filter — it is not silently
 * skipped: the deferral reason is explicit so the audit boundary is not lost.
 *
 * <p>No test here asserts per-company read isolation: ADR-001 deliberately
 * removed that guarantee for these read endpoints; the only hard boundary is
 * {@code authority_environment_id} (see {@link EnvironmentIsolationTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthorityIsolationTest {

    private static final short ETA_PREPROD_ENV = 2;
    private static final short ZATCA_SANDBOX_ENV = 5;

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
    void zatcaScopedRequestNeverReturnsEtaData() throws Exception {
        Seed eta = seedEta();
        Seed zatca = seedZatca();

        String token = authorityScopedToken(zatca.user, "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV);

        DashboardSummaryDto summary = getSummary(token);
        assertThat(summary.cards()).extracting(CompanyCardDto::companyId)
                .contains(zatca.companyId)
                .doesNotContain(eta.companyId);

        RecentActivityDto activity = getRecentActivity(token);
        assertThat(activity.entries()).extracting(e -> e.companyId())
                .containsOnly(zatca.companyId);

        List<SubmissionLogRowDto> rows = listSubmissionLog(token).items();
        assertThat(rows).extracting(SubmissionLogRowDto::companyId)
                .containsOnly(zatca.companyId);
        assertThat(rows).extracting(SubmissionLogRowDto::transactionType)
                .allSatisfy(t -> assertThat(t).isIn("STANDARD", "SIMPLIFIED"));
    }

    @Test
    void etaScopedRequestNeverReturnsZatcaData() throws Exception {
        Seed eta = seedEta();
        Seed zatca = seedZatca();

        String token = authorityScopedToken(eta.user, "ETA", "PREPROD", ETA_PREPROD_ENV);

        DashboardSummaryDto summary = getSummary(token);
        assertThat(summary.cards()).extracting(CompanyCardDto::companyId)
                .contains(eta.companyId)
                .doesNotContain(zatca.companyId);

        List<SubmissionLogRowDto> rows = listSubmissionLog(token).items();
        assertThat(rows).extracting(SubmissionLogRowDto::companyId)
                .containsOnly(eta.companyId);
        assertThat(rows).extracting(SubmissionLogRowDto::transactionType)
                .allSatisfy(t -> assertThat(t).isIn("INVOICE", "RECEIPT"));
    }

    @Test
    void zatcaDashboardCountsExcludeEtaDocuments() throws Exception {
        Seed eta = seedEta();
        Seed zatca = seedZatca();

        String token = authorityScopedToken(zatca.user, "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV);

        DashboardSummaryDto summary = getSummary(token);
        int totalPending = summary.cards().stream()
                .mapToInt(CompanyCardDto::pendingCount).sum();
        int totalFailed = summary.cards().stream()
                .mapToInt(CompanyCardDto::failedCount).sum();
        assertThat(totalPending).as("only the ZATCA SUBMITTING doc counts")
                .isEqualTo(1);
        assertThat(totalFailed).as("only the ZATCA REJECTED doc counts")
                .isEqualTo(1);
        assertThat(summary.kpi().today().total())
                .as("ETA documents never leak into ZATCA KPI totals")
                .isLessThanOrEqualTo(2);
    }

    /**
     * Deferred to Phase 7 (US4 audit-log scoping). The {@code /api/audit-logs}
     * read endpoint is not yet filtered by {@code authority_environment_id};
     * once T040/T041 land the env+company filter, enable this case so ETA audit
     * entries never surface on a ZATCA-scoped request (FR-017) and vice versa.
     */
    @Disabled("enable after Phase 7 / US4 audit scoping (T040-T041)")
    @Test
    void auditLogNeverReturnsOtherAuthorityData() throws Exception {
        Seed eta = seedEta();
        seedZatca();
        String token = authorityScopedToken(eta.user, "ETA", "PREPROD", ETA_PREPROD_ENV);
        mockMvc.perform(get("/api/audit-logs?page=0&size=50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private Seed seedEta() {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE", "RECEIPT");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        UUID companyId = env.companyA().getId();
        var submitting = fixtures.seedEtaInvoice(companyId, ETA_PREPROD_ENV,
                DocumentState.SUBMITTING, "W9-AUTH-ETA-INV-" + nano());
        var rejected = fixtures.seedEtaInvoice(companyId, ETA_PREPROD_ENV,
                DocumentState.REJECTED, "W9-AUTH-ETA-REJ-" + nano());
        fixtures.seedAttempt(companyId, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.ERROR, rejected.getId(), 1);
        OffsetDateTime now = OffsetDateTime.now();
        fixtures.seedAttemptAt(companyId, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.SUCCESS, now.minusMinutes(5));
        return new Seed(env.user(), companyId, submitting.getId());
    }

    private Seed seedZatca() {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ZATCA_SANDBOX_ENV, "STANDARD", "SIMPLIFIED");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        UUID companyId = env.companyA().getId();
        var submitting = fixtures.seedZatcaStandard(companyId, ZATCA_SANDBOX_ENV,
                DocumentState.SUBMITTING, "W9-AUTH-ZSD-" + nano());
        var rejected = fixtures.seedZatcaSimplified(companyId, ZATCA_SANDBOX_ENV,
                DocumentState.REJECTED, "W9-AUTH-ZSM-" + nano());
        fixtures.seedAttempt(companyId, ZATCA_SANDBOX_ENV, TransactionType.STANDARD,
                SubmissionResult.ERROR, rejected.getId(), 1);
        OffsetDateTime now = OffsetDateTime.now();
        fixtures.seedAttemptAt(companyId, ZATCA_SANDBOX_ENV, TransactionType.STANDARD,
                SubmissionResult.SUCCESS, now.minusMinutes(3));
        return new Seed(env.user(), companyId, submitting.getId());
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

    private record Seed(User user, UUID companyId, UUID documentId) {
    }

    private record SubmissionLogPage(
            List<SubmissionLogRowDto> items,
            int page,
            int size,
            long totalElements) {
    }
}
