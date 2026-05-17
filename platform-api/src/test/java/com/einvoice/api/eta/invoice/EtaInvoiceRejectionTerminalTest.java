package com.einvoice.api.eta.invoice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceResponse;
import com.einvoice.api.eta.invoice.service.EtaInvoiceService;
import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.error.DocumentNotDraftException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class EtaInvoiceRejectionTerminalTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EtaInvoiceService service;

    @Test
    void rejectedIsTerminal_editReturns409DocumentNotDraft() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Mockito.doThrow(new DocumentNotDraftException("Only DRAFT invoices can be edited", "REJECTED"))
                .when(service).update(eq(docId), any(), eq(0));

        String updateBody = """
                {"invoiceNumber":"INV-EDITED","documentType":"i",
                 "issueDatetime":"2026-05-13T10:00:00+02:00",
                 "sellerData":{},"buyerData":{},"currency":"EGP","lines":[]}""";

        mvc.perform(put("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_DRAFT"));
    }

    @Test
    void rejectedIsTerminal_deleteReturns409DocumentNotDraft() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        Mockito.doThrow(new DocumentNotDraftException("Only DRAFT invoices can be deleted", "REJECTED"))
                .when(service).delete(eq(docId));

        mvc.perform(delete("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_DRAFT"));
    }

    @Test
    void cloneAsDraft_createsNewDraftFromRejected() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID cloneId = UUID.randomUUID();

        when(service.cloneAsDraft(eq(docId), eq("CLONE-001")))
                .thenReturn(buildResponse(cloneId, companyId, "CLONE-001", EtaInvoiceState.DRAFT, 0));

        mvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/clone-as-draft",
                        companyId, docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceNumber\":\"CLONE-001\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.id").value(cloneId.toString()))
                .andExpect(jsonPath("$.invoiceNumber").value("CLONE-001"))
                .andExpect(jsonPath("$.state").value("DRAFT"));
    }

    private static EtaInvoiceResponse buildResponse(
            UUID id, UUID companyId, String invoiceNumber,
            EtaInvoiceState state, Integer version) {
        return new EtaInvoiceResponse(
                id, companyId, null,
                invoiceNumber, EtaInvoiceDocumentType.i,
                "1.0", OffsetDateTime.now(),
                LocalDate.now(),
                Collections.emptyMap(), Collections.emptyMap(),
                null, null, null, null, null, null,
                Collections.emptyMap(), Collections.emptyMap(),
                "EGP",
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                null, state, version,
                null, null, null,
                null, null, null,
                Collections.emptyList());
    }
}
