package com.einvoice.api.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.service.submission.SubmissionLogQuery;
import com.einvoice.core.service.submission.SubmissionLogQueryService;
import com.einvoice.core.service.submission.SubmissionLogReadModels.SubmissionLogRow;
import com.einvoice.security.tenant.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test for {@code GET /api/submission-log} under the company-less
 * {@code AUTHORITY_SCOPED} model (ADR-001). The query service is mocked so this
 * pins the wire shape — the {@code items/page/size/totalElements} envelope, the
 * row fields (including {@code IN_FLIGHT} outcome), the {@code submittedAt DESC}
 * default sort, and the absence of a VIEW/mode gate.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class SubmissionLogContractTest {

    private static final short ZATCA_SANDBOX_ENV = 5;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubmissionLogQueryService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), null, ZATCA_SANDBOX_ENV,
                "ZATCA", "SANDBOX", TenantContext.Mode.AUTHORITY_SCOPED,
                false, System.currentTimeMillis(), "jti"));
        when(service.list(any(), any())).thenReturn(samplePage());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listIsReachableInAuthorityScopedModeWithNoViewGate() throws Exception {
        mvc.perform(get("/api/submission-log"))
                .andExpect(status().isOk());
    }

    @Test
    void listReturnsItemsPageSizeTotalElementsEnvelope() throws Exception {
        mvc.perform(get("/api/submission-log"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listExposesRowShapeWithAllFields() throws Exception {
        mvc.perform(get("/api/submission-log"))
                .andExpect(jsonPath("$.items[0].attemptId").isString())
                .andExpect(jsonPath("$.items[0].companyId").isString())
                .andExpect(jsonPath("$.items[0].companyName").value("Acme LLC"))
                .andExpect(jsonPath("$.items[0].transactionType")
                        .value("SIMPLIFIED"))
                .andExpect(jsonPath("$.items[0].documentId").isString())
                .andExpect(jsonPath("$.items[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.items[0].outcome").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].statusCode").value(400))
                .andExpect(jsonPath("$.items[0].errorSummary")
                        .value("VAT category invalid"))
                .andExpect(jsonPath("$.items[0].submittedAt").isString())
                .andExpect(jsonPath("$.items[0].completedAt").isString())
                .andExpect(jsonPath("$.items[0].submittedBy").isString());
    }

    @Test
    void listReflectsInFlightOutcomeOnTheWire() throws Exception {
        when(service.list(any(), any())).thenReturn(singleRowPage(
                new SubmissionLogRow(
                        UUID.randomUUID(), UUID.randomUUID(), "Zatco",
                        TransactionType.STANDARD.name(), UUID.randomUUID(), 1,
                        "IN_FLIGHT", null, null,
                        OffsetDateTime.parse("2026-06-02T09:14:00Z"),
                        null, null)));
        mvc.perform(get("/api/submission-log"))
                .andExpect(jsonPath("$.items[0].outcome").value("IN_FLIGHT"))
                .andExpect(jsonPath("$.items[0].statusCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].completedAt").doesNotExist());
    }

    @Test
    void listAppliesSubmittedAtDescDefaultSortAndPagingDefaults() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        mvc.perform(get("/api/submission-log"))
                .andExpect(status().isOk());
        verify(service).list(any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("submittedAt"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listForwardsPagingAndFiltersToService() throws Exception {
        ArgumentCaptor<SubmissionLogQuery> query =
                ArgumentCaptor.forClass(SubmissionLogQuery.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        UUID companyId = UUID.randomUUID();
        mvc.perform(get("/api/submission-log")
                        .param("page", "1")
                        .param("size", "5")
                        .param("companyId", companyId.toString())
                        .param("transactionType", "INVOICE")
                        .param("outcome", "IN_FLIGHT")
                        .param("dateFrom", "2026-06-01T00:00:00Z")
                        .param("dateTo", "2026-06-30T23:59:59Z"))
                .andExpect(status().isOk());
        verify(service).list(query.capture(), pageable.capture());
        assertThat(query.getValue().envId()).isEqualTo(ZATCA_SANDBOX_ENV);
        assertThat(query.getValue().filterCompanyId()).isEqualTo(companyId);
        assertThat(query.getValue().transactionType())
                .isEqualTo(TransactionType.INVOICE);
        assertThat(query.getValue().inFlight())
                .as("IN_FLIGHT outcome binds as null result + inFlight flag")
                .isTrue();
        assertThat(query.getValue().outcomeResult()).isNull();
        assertThat(query.getValue().dateFrom())
                .isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        assertThat(query.getValue().dateTo())
                .isEqualTo(OffsetDateTime.parse("2026-06-30T23:59:59Z"));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void listBindsFinalizedOutcomeAsResultEnum() throws Exception {
        ArgumentCaptor<SubmissionLogQuery> query =
                ArgumentCaptor.forClass(SubmissionLogQuery.class);
        mvc.perform(get("/api/submission-log").param("outcome", "ERROR"))
                .andExpect(status().isOk());
        verify(service).list(query.capture(), any());
        assertThat(query.getValue().outcomeResult())
                .isEqualTo(SubmissionResult.ERROR);
        assertThat(query.getValue().inFlight()).isFalse();
    }

    @Test
    void listRejectsUnparseableFiltersWithBadRequest() throws Exception {
        mvc.perform(get("/api/submission-log").param("outcome", "BOGUS"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/submission-log").param("transactionType", "NOPE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/submission-log").param("dateFrom", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listClampsOversizedPageSize() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        mvc.perform(get("/api/submission-log").param("size", "100000"))
                .andExpect(status().isOk());
        verify(service).list(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    }

    private PageImpl<SubmissionLogRow> samplePage() {
        return singleRowPage(new SubmissionLogRow(
                UUID.randomUUID(), UUID.randomUUID(), "Acme LLC",
                TransactionType.SIMPLIFIED.name(), UUID.randomUUID(), 1,
                SubmissionResult.REJECTED.name(), 400, "VAT category invalid",
                OffsetDateTime.parse("2026-06-02T09:14:00Z"),
                OffsetDateTime.parse("2026-06-02T09:14:03Z"),
                UUID.randomUUID()));
    }

    private PageImpl<SubmissionLogRow> singleRowPage(SubmissionLogRow row) {
        return new PageImpl<>(List.of(row),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt")),
                1);
    }
}
