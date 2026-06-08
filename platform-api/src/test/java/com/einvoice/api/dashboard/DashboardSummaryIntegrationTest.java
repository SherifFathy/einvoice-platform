package com.einvoice.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.dashboard.dto.CompanyCardDto;
import com.einvoice.api.dashboard.dto.DashboardSummaryDto;
import com.einvoice.api.dashboard.dto.RecentActivityDto;
import com.einvoice.api.support.Wave9Fixtures;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.user.User;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
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
 * Integration test for the operator dashboard under the company-less
 * {@code AUTHORITY_SCOPED} model (ADR-001), exercising the real query service
 * against a seeded database: pending/failed count rules (including the
 * latest-attempt {@code ERROR/TIMEOUT} rule without double-counting),
 * certificate-expiry warnings, cross-company card coverage, hard
 * {@code authority_environment_id} isolation, and the recent-activity feed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DashboardSummaryIntegrationTest {

    private static final short ETA_PREPROD_ENV = 2;
    private static final short ZATCA_SANDBOX_ENV = 5;
    /** ETA Production — a different, real authority environment (V37). */
    private static final short OTHER_ENV = 1;

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
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdCompanyIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void summaryAggregatesPendingAndFailedAcrossAllCompaniesInEnv() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE", "RECEIPT");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        String token = authorityScopedToken(env.user(), "ETA", "PREPROD", ETA_PREPROD_ENV);

        UUID a = env.companyA().getId();
        UUID b = env.companyB().getId();
        fixtures.seedEtaInvoice(a, ETA_PREPROD_ENV, DocumentState.SUBMITTING, "W9-INV-A1");
        fixtures.seedEtaInvoice(a, ETA_PREPROD_ENV, DocumentState.ACCEPTED, "W9-INV-A2");
        fixtures.seedEtaReceipt(a, ETA_PREPROD_ENV, DocumentState.SUBMITTED, "W9-REC-A1");
        var rejectedA = fixtures.seedEtaInvoice(a, ETA_PREPROD_ENV,
                DocumentState.REJECTED, "W9-INV-A3");
        fixtures.seedAttempt(a, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.ERROR, rejectedA.getId(), 1);

        fixtures.seedEtaInvoice(b, ETA_PREPROD_ENV, DocumentState.REJECTED, "W9-INV-B1");
        fixtures.seedEtaInvoice(b, ETA_PREPROD_ENV, DocumentState.IN_REVIEW, "W9-INV-B2");
        var submittedB = fixtures.seedEtaInvoice(b, ETA_PREPROD_ENV,
                DocumentState.SUBMITTED, "W9-INV-B3");
        fixtures.seedAttempt(b, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.ERROR, submittedB.getId(), 1);

        DashboardSummaryDto summary = getSummary(token);

        CompanyCardDto cardA = card(summary, a);
        assertThat(cardA.pendingCount()).isEqualTo(2);
        assertThat(cardA.failedCount()).as("rejected A3 once (also latest ERROR)").isEqualTo(1);
        assertThat(cardA.certificate()).as("ETA has no signing certificate").isNull();

        CompanyCardDto cardB = card(summary, b);
        assertThat(cardB.pendingCount()).as("IN_REVIEW + SUBMITTED").isEqualTo(2);
        assertThat(cardB.failedCount()).as("REJECTED B1 + latest-ERROR B3").isEqualTo(2);

        assertThat(summary.kpi().today().total()).isEqualTo(7);
        assertThat(summary.kpi().today().byStatus())
                .containsEntry("SUBMITTING", 1)
                .containsEntry("SUBMITTED", 2)
                .containsEntry("IN_REVIEW", 1)
                .containsEntry("ACCEPTED", 1)
                .containsEntry("REJECTED", 2)
                .containsEntry("DRAFT", 0)
                .containsEntry("CANCELLED", 0);
        assertThat(summary.kpi().thisMonth().total()).isEqualTo(7);
    }

    @Test
    void summaryExcludesDataFromOtherAuthorityEnvironment() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE", "RECEIPT");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        final String token = authorityScopedToken(env.user(), "ETA", "PREPROD", ETA_PREPROD_ENV);

        UUID otherCompany = fixtures.createCompany("Other Env Co", "3000000990001").getId();
        createdCompanyIds.add(otherCompany);
        fixtures.grantRole(env.user().getId(), otherCompany, OTHER_ENV, "INVOICE", "ACCOUNTANT");
        fixtures.seedEtaInvoice(otherCompany, OTHER_ENV,
                DocumentState.SUBMITTING, "W9-INV-OTHER-1");
        fixtures.seedEtaInvoice(otherCompany, OTHER_ENV,
                DocumentState.REJECTED, "W9-INV-OTHER-2");

        fixtures.seedEtaInvoice(env.companyA().getId(), ETA_PREPROD_ENV,
                DocumentState.SUBMITTING, "W9-INV-A1");

        DashboardSummaryDto summary = getSummary(token);

        assertThat(summary.cards()).extracting(CompanyCardDto::companyId)
                .doesNotContain(otherCompany)
                .contains(env.companyA().getId());
        assertThat(summary.kpi().today().total())
                .as("only the single env-2 doc is counted")
                .isEqualTo(1);
    }

    @Test
    void summaryCertWarningForZatcaCompanyUnder30Days() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ZATCA_SANDBOX_ENV, "STANDARD", "SIMPLIFIED");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        String token = authorityScopedToken(env.user(), "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV);

        fixtures.seedZatcaConfigExpiringIn(env.companyA().getId(), ZATCA_SANDBOX_ENV, 15);
        fixtures.seedZatcaStandard(env.companyA().getId(), ZATCA_SANDBOX_ENV,
                DocumentState.ACCEPTED, "W9-ZSD-1");

        DashboardSummaryDto summary = getSummary(token);

        CompanyCardDto cardA = card(summary, env.companyA().getId());
        assertThat(cardA.certificate()).as("ZATCA company has a signing certificate")
                .isNotNull();
        assertThat(cardA.certificate().expiringSoon()).isTrue();
        assertThat(cardA.certificate().expired()).isFalse();
        assertThat(cardA.certificate().daysRemaining()).isBetween(14, 16);

        CompanyCardDto cardB = card(summary, env.companyB().getId());
        assertThat(cardB.certificate()).as("no config → no certificate status").isNull();
    }

    @Test
    void recentActivityReturnsNewestFirstWithInFlight() throws Exception {
        Wave9Fixtures.AssignedCompanies env = fixtures.seedAssignedCompanies(
                ETA_PREPROD_ENV, "INVOICE", "RECEIPT");
        track(env.user().getId(), env.companyA().getId(), env.companyB().getId());
        String token = authorityScopedToken(env.user(), "ETA", "PREPROD", ETA_PREPROD_ENV);

        OffsetDateTime now = OffsetDateTime.now();
        UUID a = env.companyA().getId();
        UUID b = env.companyB().getId();
        fixtures.seedAttemptAt(a, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.SUCCESS, now.minusMinutes(10));
        fixtures.seedAttemptAt(b, ETA_PREPROD_ENV, TransactionType.RECEIPT,
                null, now.minusMinutes(5));
        fixtures.seedAttemptAt(a, ETA_PREPROD_ENV, TransactionType.INVOICE,
                SubmissionResult.REJECTED, now);

        RecentActivityDto activity = getRecentActivity(token);

        assertThat(activity.entries()).hasSize(3);
        assertThat(activity.entries()).extracting(e -> e.outcome())
                .containsExactly("REJECTED", "IN_FLIGHT", "SUCCESS");
        assertThat(activity.entries()).isSortedAccordingTo(
                (x, y) -> y.submittedAt().compareTo(x.submittedAt()));
    }

    private DashboardSummaryDto getSummary(String token) throws Exception {
        return read(token, "/api/dashboard/summary", DashboardSummaryDto.class);
    }

    private RecentActivityDto getRecentActivity(String token) throws Exception {
        return read(token, "/api/dashboard/recent-activity", RecentActivityDto.class);
    }

    private <T> T read(String token, String path, Class<T> type) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    }

    private String authorityScopedToken(User user, String authority, String environment,
            short envId) {
        return jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                authority, environment, envId, null,
                TenantContext.Mode.AUTHORITY_SCOPED);
    }

    private static CompanyCardDto card(DashboardSummaryDto summary, UUID companyId) {
        return summary.cards().stream()
                .filter(c -> c.companyId().equals(companyId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("card not found: " + companyId));
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
}
