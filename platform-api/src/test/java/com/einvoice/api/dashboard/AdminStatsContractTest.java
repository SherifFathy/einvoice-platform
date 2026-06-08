package com.einvoice.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.service.dashboard.AdminStatsQueryService;
import com.einvoice.core.service.dashboard.SystemStats;
import com.einvoice.security.tenant.TenantContext;
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
 * Contract test for {@code GET /api/admin/stats} (Admin-Mode / Super-User only,
 * counts-only response per Constitution VII.3). The query service is mocked so
 * this pins the wire shape and the Admin-Mode/Super-User auth guard. The UTC
 * day-boundary computation itself is exercised by
 * {@link AdminStatsIntegrationTest} against the real service.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AdminStatsContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminStatsQueryService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(adminModeHolder(true));
        when(service.stats(any())).thenReturn(new SystemStats(
                (short) 3, 18L, 42L, 57L));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void statsIsReachableForSuperUserInAdminMode() throws Exception {
        mvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk());
    }

    @Test
    void statsExposesCountsOnlyShape() throws Exception {
        mvc.perform(get("/api/admin/stats"))
                .andExpect(jsonPath("$.authorityEnvironmentId").value(3))
                .andExpect(jsonPath("$.totalCompanies").value(18))
                .andExpect(jsonPath("$.totalUsers").value(42))
                .andExpect(jsonPath("$.totalSubmissionsToday").value(57));
    }

    @Test
    void statsResponseCarriesNoOperationalDocumentContent() throws Exception {
        mvc.perform(get("/api/admin/stats"))
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.cards").doesNotExist())
                .andExpect(jsonPath("$.kpi").doesNotExist())
                .andExpect(jsonPath("$.entries").doesNotExist());
    }

    @Test
    void statsRejectsNonSuperUser() throws Exception {
        TenantContext.set(adminModeHolder(false));
        mvc.perform(get("/api/admin/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statsRejectsSuperUserOutsideAdminMode() throws Exception {
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), null, (short) 3,
                "ZATCA", "SANDBOX", TenantContext.Mode.AUTHORITY_SCOPED,
                true, System.currentTimeMillis(), "jti"));
        mvc.perform(get("/api/admin/stats"))
                .andExpect(status().isForbidden());
    }

    private static TenantContext.Holder adminModeHolder(boolean isSuperUser) {
        return new TenantContext.Holder(
                UUID.randomUUID(), null, (short) 3,
                "ZATCA", "SANDBOX", TenantContext.Mode.ADMIN_MODE,
                isSuperUser, System.currentTimeMillis(), "jti");
    }
}
