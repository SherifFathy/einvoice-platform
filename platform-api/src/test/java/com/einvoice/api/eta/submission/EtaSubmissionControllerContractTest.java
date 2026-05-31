package com.einvoice.api.eta.submission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.eta.invoice.service.EtaInvoiceService;
import com.einvoice.api.eta.submission.service.EtaSubmissionOrchestrator;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class EtaSubmissionControllerContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EtaInvoiceService invoiceService;

    @MockitoBean
    private EtaSubmissionOrchestrator orchestrator;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID companyId;
    private UUID docId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        docId = UUID.randomUUID();
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), companyId, (short) 2,
                "ETA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
                true, System.currentTimeMillis(), "jti"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void submitInvoiceReturns200WithState() throws Exception {
        EtaInvoiceHeader header = EtaInvoiceHeader.builder()
                .id(docId).companyId(companyId)
                .authorityEnvironmentId((short) 2)
                .state(DocumentState.ACCEPTED)
                .etaUuid("eta-uuid-123")
                .build();
        when(invoiceService.loadWithinTenant(docId)).thenReturn(header);
        when(orchestrator.submit(any())).thenReturn(header);

        mvc.perform(post("/api/companies/{companyId}/eta"
                        + "/invoices/{docId}/submit",
                        companyId, docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.etaUuid").value("eta-uuid-123"));
    }

    @Test
    void retryInvoiceReturns200WithState() throws Exception {
        EtaInvoiceHeader header = EtaInvoiceHeader.builder()
                .id(docId).companyId(companyId)
                .authorityEnvironmentId((short) 2)
                .state(DocumentState.SUBMITTING)
                .etaUuid("eta-uuid-456")
                .etaSubmissionId("sub-456")
                .build();
        when(invoiceService.loadWithinTenant(docId)).thenReturn(header);
        when(orchestrator.retry(any())).thenReturn(header);

        mvc.perform(post("/api/companies/{companyId}/eta"
                        + "/invoices/{docId}/retry",
                        companyId, docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUBMITTING"))
                .andExpect(jsonPath("$.etaUuid").value("eta-uuid-456"));
    }

    @Test
    void cancelInvoiceReturns200WithCancelledState() throws Exception {
        EtaInvoiceHeader cancelled = EtaInvoiceHeader.builder()
                .id(docId).companyId(companyId)
                .authorityEnvironmentId((short) 2)
                .state(DocumentState.CANCELLED)
                .build();
        when(invoiceService.loadWithinTenant(docId)).thenReturn(
                EtaInvoiceHeader.builder()
                        .id(docId).companyId(companyId)
                        .authorityEnvironmentId((short) 2)
                        .state(DocumentState.ACCEPTED)
                        .etaUuid("eta-uuid-789")
                        .build());
        when(orchestrator.cancel(any(), any())).thenReturn(cancelled);

        mvc.perform(post("/api/companies/{companyId}/eta"
                        + "/invoices/{docId}/cancel",
                        companyId, docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"));
    }
}
