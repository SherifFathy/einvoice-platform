package com.einvoice.api.eta.invoice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceResponse;
import com.einvoice.api.eta.invoice.service.EtaInvoiceService;
import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.InvalidUnitValueException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class EtaInvoiceControllerContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EtaInvoiceService service;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID companyId;
    private UUID docId;
    private EtaInvoiceResponse sampleResponse;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        docId = UUID.randomUUID();
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), companyId, (short) 2,
                "ETA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
                true, System.currentTimeMillis(), "jti"));
        sampleResponse = new EtaInvoiceResponse(
                docId, companyId, null,
                "INV-001", EtaInvoiceDocumentType.i,
                "1.0", OffsetDateTime.now(), null,
                Map.of(), Map.of(), null, null, null, null, null,
                null, Map.of(), Map.of(),
                "EGP",
                new BigDecimal("100.00000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("100.00000"), new BigDecimal("114.00000"),
                null, DocumentState.DRAFT, 0,
                null, null, null,
                null, OffsetDateTime.now(), OffsetDateTime.now(),
                Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listReturnsPagedResults() throws Exception {
        Page<EtaInvoiceResponse> page = new PageImpl<>(List.of(sampleResponse),
                PageRequest.of(0, 50), 1);
        when(service.list(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mvc.perform(get("/api/companies/{companyId}/eta/invoices", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("INV-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createReturns201WithETag() throws Exception {
        when(service.create(any(), any())).thenReturn(sampleResponse);

        mvc.perform(post("/api/companies/{companyId}/eta/invoices", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "INV-001",
                                        "documentType", "i",
                                        "issueDatetime", "2026-05-13T10:00:00+02:00",
                                        "sellerData", Map.of(),
                                        "buyerData", Map.of(),
                                        "currency", "EGP",
                                        "lines", List.of(Map.of(
                                                "itemType", "GS1",
                                                "itemCode", "123",
                                                "description", "Test",
                                                "unitType", "EA",
                                                "quantity", "1.00000",
                                                "unitValue", Map.of(
                                                        "currencySold", "EGP",
                                                        "amountEGP", "100",
                                                        "amountSold", "100",
                                                        "currencyExchangeRate", "1")))))))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""));
    }

    @Test
    void getReturns200WithETag() throws Exception {
        when(service.findById(docId)).thenReturn(sampleResponse);

        mvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-001"));
    }

    @Test
    void putWithValidIfMatchReturns200() throws Exception {
        EtaInvoiceResponse updated = new EtaInvoiceResponse(
                docId, companyId, null, "INV-001", EtaInvoiceDocumentType.i,
                "1.0", OffsetDateTime.now(), null, Map.of(), Map.of(),
                null, null, null, null, null, null, Map.of(), Map.of(),
                "EGP", new BigDecimal("200.00000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("200.00000"), new BigDecimal("228.00000"),
                null, DocumentState.DRAFT, 1,
                null, null, null, null, OffsetDateTime.now(), OffsetDateTime.now(),
                Collections.emptyList());
        when(service.update(eq(docId), any(), eq(0))).thenReturn(updated);

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
                                        "lines", List.of(Map.of(
                                                "itemType", "GS1",
                                                "itemCode", "123",
                                                "description", "Updated",
                                                "unitType", "EA",
                                                "quantity", "1.00000",
                                                "unitValue", Map.of(
                                                        "currencySold", "EGP",
                                                        "amountEGP", "200",
                                                        "amountSold", "200",
                                                        "currencyExchangeRate", "1")))))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    void putWithStaleIfMatchReturns409() throws Exception {
        when(service.update(eq(docId), any(), eq(0)))
                .thenThrow(new OptimisticLockConflictException("Conflict",
                        0, 1, sampleResponse));

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
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void deleteDraftReturns204() throws Exception {
        mvc.perform(delete("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNonDraftReturns409() throws Exception {
        org.mockito.Mockito.doThrow(new DocumentNotDraftException("Not draft",
                        "SUBMITTING"))
                .when(service).delete(docId);

        mvc.perform(delete("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_DRAFT"));
    }

    @Test
    void createWithMissingUnitValueKeyReturns400() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new InvalidUnitValueException(
                        "Missing key", "unitValue", List.of("amountEGP")));

        mvc.perform(post("/api/companies/{companyId}/eta/invoices", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "INV-002",
                                        "documentType", "i",
                                        "issueDatetime", "2026-05-13T10:00:00+02:00",
                                        "sellerData", Map.of(),
                                        "buyerData", Map.of(),
                                        "currency", "EGP",
                                        "lines", List.of(Map.of(
                                                "itemType", "GS1",
                                                "itemCode", "123",
                                                "description", "Bad",
                                                "unitType", "EA",
                                                "quantity", "1",
                                                "unitValue", Map.of(
                                                        "currencySold", "EGP")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UNIT_VALUE"));
    }
}
