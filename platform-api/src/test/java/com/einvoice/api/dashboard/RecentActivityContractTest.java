package com.einvoice.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.service.dashboard.DashboardQueryService;
import com.einvoice.core.service.dashboard.DashboardReadModels.RecentActivity;
import com.einvoice.core.service.dashboard.DashboardReadModels.RecentActivityEntry;
import com.einvoice.security.tenant.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
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
 * Contract test for {@code GET /api/dashboard/recent-activity} under the
 * company-less {@code AUTHORITY_SCOPED} model. The query service is mocked so
 * this pins the entry shape, the {@code IN_FLIGHT} outcome, and the
 * "fewer-than-10 returns all" behaviour at the controller layer.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class RecentActivityContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardQueryService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), null, (short) 5,
                "ZATCA", "SANDBOX", TenantContext.Mode.AUTHORITY_SCOPED,
                false, System.currentTimeMillis(), "jti"));
        when(service.recentActivity(any())).thenReturn(sampleActivity());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recentActivityIsReachableInAuthorityScopedMode() throws Exception {
        mvc.perform(get("/api/dashboard/recent-activity"))
                .andExpect(status().isOk());
    }

    @Test
    void recentActivityExposesEntryShape() throws Exception {
        mvc.perform(get("/api/dashboard/recent-activity"))
                .andExpect(jsonPath("$.entries[0].attemptId").isString())
                .andExpect(jsonPath("$.entries[0].companyId").isString())
                .andExpect(jsonPath("$.entries[0].companyName").value("Acme LLC"))
                .andExpect(jsonPath("$.entries[0].transactionType").value("STANDARD"))
                .andExpect(jsonPath("$.entries[0].documentId").isString())
                .andExpect(jsonPath("$.entries[0].outcome").value("REJECTED"))
                .andExpect(jsonPath("$.entries[0].submittedAt").isString());
    }

    @Test
    void recentActivitySurfacesInFlightOutcome() throws Exception {
        mvc.perform(get("/api/dashboard/recent-activity"))
                .andExpect(jsonPath("$.entries[2].outcome").value("IN_FLIGHT"));
    }

    @Test
    void recentActivityReturnsAllAvailableWhenFewerThanTen() throws Exception {
        mvc.perform(get("/api/dashboard/recent-activity"))
                .andExpect(jsonPath("$.entries.length()").value(3));
    }

    private RecentActivity sampleActivity() {
        OffsetDateTime base = OffsetDateTime.parse("2026-06-02T09:14:00Z");
        return new RecentActivity(List.of(
                new RecentActivityEntry(UUID.randomUUID(), UUID.randomUUID(),
                        "Acme LLC", "STANDARD", UUID.randomUUID(),
                        "REJECTED", base),
                new RecentActivityEntry(UUID.randomUUID(), UUID.randomUUID(),
                        "Zatco", "SIMPLIFIED", UUID.randomUUID(),
                        "SUCCESS", base.minusMinutes(5)),
                new RecentActivityEntry(UUID.randomUUID(), UUID.randomUUID(),
                        "Acme LLC", "STANDARD", UUID.randomUUID(),
                        "IN_FLIGHT", base.minusMinutes(10))));
    }
}
