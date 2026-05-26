package com.einvoice.api.eta.invoice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceResponse;
import com.einvoice.api.eta.invoice.service.EtaInvoiceService;
import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
class EtaInvoiceConflictTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EtaInvoiceService service;

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
    void optimisticLockConflictReturns409WithDetails() throws Exception {

        EtaInvoiceResponse current = new EtaInvoiceResponse(
                docId, companyId, null, "INV-001", EtaInvoiceDocumentType.i,
                "1.0", OffsetDateTime.now(), null, Map.of(), Map.of(),
                null, null, null, null, null, null, Map.of(), Map.of(),
                "EGP", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, DocumentState.DRAFT, 1, null, null, null,
                null, OffsetDateTime.now(), OffsetDateTime.now(),
                Collections.emptyList());

        when(service.update(eq(docId), any(), eq(0)))
                .thenThrow(new OptimisticLockConflictException("Conflict", 0, 1, current));

        mvc.perform(put("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "INV-001",
                                        "documentType", "i",
                                        "issueDatetime", "2026-05-13T10:00:00+02:00",
                                        "sellerData", Map.of(),
                                        "buyerData", Map.of(),
                                        "currency", "EGP",
                                        "lines", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"))
                .andExpect(jsonPath("$.expectedVersion").value(0))
                .andExpect(jsonPath("$.actualVersion").value(1))
                .andExpect(jsonPath("$.current").isNotEmpty());
    }
}
