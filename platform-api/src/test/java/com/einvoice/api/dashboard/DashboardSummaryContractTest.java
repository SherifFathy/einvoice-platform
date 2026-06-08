package com.einvoice.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.service.dashboard.DashboardQueryService;
import com.einvoice.core.service.dashboard.DashboardReadModels.CertificateStatus;
import com.einvoice.core.service.dashboard.DashboardReadModels.CompanyCard;
import com.einvoice.core.service.dashboard.DashboardReadModels.DashboardKpi;
import com.einvoice.core.service.dashboard.DashboardReadModels.DashboardSummary;
import com.einvoice.core.service.dashboard.DashboardReadModels.StatusBreakdown;
import com.einvoice.security.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test for {@code GET /api/dashboard/summary} under the company-less
 * {@code AUTHORITY_SCOPED} model (ADR-001). The query service is mocked so this
 * pins the wire shape — response fields, the all-seven status breakdown, the
 * {@code certificate=null} ETA behaviour, and the absence of a VIEW/mode gate.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class DashboardSummaryContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardQueryService service;

    private UUID etaCompanyId;
    private UUID zatcaCompanyId;

    @BeforeEach
    void setUp() {
        etaCompanyId = UUID.randomUUID();
        zatcaCompanyId = UUID.randomUUID();
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), null, (short) 5,
                "ZATCA", "SANDBOX", TenantContext.Mode.AUTHORITY_SCOPED,
                false, System.currentTimeMillis(), "jti"));
        when(service.summary(any())).thenReturn(sampleSummary());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void summaryIsReachableInAuthorityScopedMode() throws Exception {
        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk());
    }

    @Test
    void summaryExposesCardShape() throws Exception {
        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.cards[0].companyId").isString())
                .andExpect(jsonPath("$.cards[0].nameEn").value("Acme LLC"))
                .andExpect(jsonPath("$.cards[0].nameAr").isString())
                .andExpect(jsonPath("$.cards[0].taxNumber").value("123456789"))
                .andExpect(jsonPath("$.cards[0].active").value(true))
                .andExpect(jsonPath("$.cards[0].pendingCount").value(4))
                .andExpect(jsonPath("$.cards[0].failedCount").value(1));
    }

    @Test
    void summaryEtaCardHasNullCertificate() throws Exception {
        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.cards[0].certificate").doesNotExist());
    }

    @Test
    void summaryZatcaCardHasCertificateStatus() throws Exception {
        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.cards[1].certificate.daysRemaining").value(12))
                .andExpect(jsonPath("$.cards[1].certificate.expiringSoon").value(true))
                .andExpect(jsonPath("$.cards[1].certificate.expired").value(false));
    }

    @Test
    void summaryKpiReportsAllSevenStatusesAndTotal() throws Exception {
        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.kpi.today.total").value(12))
                .andExpect(jsonPath("$.kpi.today.byStatus.DRAFT").value(2))
                .andExpect(jsonPath("$.kpi.today.byStatus.SUBMITTING").value(1))
                .andExpect(jsonPath("$.kpi.today.byStatus.SUBMITTED").value(3))
                .andExpect(jsonPath("$.kpi.today.byStatus.IN_REVIEW").value(2))
                .andExpect(jsonPath("$.kpi.today.byStatus.ACCEPTED").value(3))
                .andExpect(jsonPath("$.kpi.today.byStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.kpi.today.byStatus.CANCELLED").value(0))
                .andExpect(jsonPath("$.kpi.thisMonth.total").value(230));
    }

    private DashboardSummary sampleSummary() {
        CompanyCard etaCard = new CompanyCard(
                etaCompanyId, "Acme LLC", "شركة", "123456789",
                true, 4, 1, null);
        CompanyCard zatcaCard = new CompanyCard(
                zatcaCompanyId, "Zatco", "شركة", "987654321",
                true, 2, 0, new CertificateStatus(12, true, false));
        StatusBreakdown today = new StatusBreakdown(12, statusMap(
                2, 1, 3, 2, 3, 1, 0));
        StatusBreakdown month = new StatusBreakdown(230, statusMap(
                20, 4, 30, 25, 140, 9, 2));
        return new DashboardSummary(
                List.of(etaCard, zatcaCard), new DashboardKpi(today, month));
    }

    private static Map<String, Integer> statusMap(
            int draft, int submitting, int submitted, int inReview,
            int accepted, int rejected, int cancelled) {
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        byStatus.put("DRAFT", draft);
        byStatus.put("SUBMITTING", submitting);
        byStatus.put("SUBMITTED", submitted);
        byStatus.put("IN_REVIEW", inReview);
        byStatus.put("ACCEPTED", accepted);
        byStatus.put("REJECTED", rejected);
        byStatus.put("CANCELLED", cancelled);
        return byStatus;
    }
}
