package com.einvoice.api.eta.receipt;

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

import com.einvoice.api.eta.receipt.service.EtaReceiptFormMapper.EtaReceiptResponse;
import com.einvoice.api.eta.receipt.service.EtaReceiptService;
import com.einvoice.core.domain.eta.document.EtaReceiptDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.InvalidUnitValueException;
import com.einvoice.core.error.MissingOriginalDocumentException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class EtaReceiptControllerContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EtaReceiptService service;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID companyId;
    private UUID docId;
    private EtaReceiptResponse sampleResponse;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        docId = UUID.randomUUID();
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), companyId, (short) 2,
                "ETA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
                true, System.currentTimeMillis(), "jti"));
        sampleResponse = new EtaReceiptResponse(
                docId, companyId, null,
                "REC-001", EtaReceiptDocumentType.r,
                "1.2", OffsetDateTime.now(),
                Map.of(), null,
                "POS-001", "CASH",
                "EGP",
                new BigDecimal("50.00000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("50.00000"),
                new BigDecimal("57.00000"),
                null, DocumentState.DRAFT, 0,
                null, null,
                null, OffsetDateTime.now(), OffsetDateTime.now(),
                Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listReturnsPagedResults() throws Exception {
        Page<EtaReceiptResponse> page = new PageImpl<>(
                List.of(sampleResponse),
                PageRequest.of(0, 50), 1);
        when(service.list(any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(page);

        mvc.perform(get("/api/companies/{companyId}/eta/receipts",
                companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].receiptNumber")
                        .value("REC-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createReturns201WithETag() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse);

        mvc.perform(post("/api/companies/{companyId}/eta/receipts",
                companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiptNumber", "REC-001",
                                        "documentType", "r",
                                        "issueDatetime",
                                                "2026-05-13T14:30:00+02:00",
                                        "sellerData", Map.of(),
                                        "currency", "EGP",
                                        "lines", List.of(Map.of(
                                                "itemType", "EGS",
                                                "itemCode", "EGS-001",
                                                "description", "Test",
                                                "unitType", "EA",
                                                "quantity", "2.00000",
                                                "unitValue", Map.of(
                                                        "currencySold", "EGP",
                                                        "amountEGP", "25",
                                                        "amountSold", "25",
                                                        "currencyExchangeRate",
                                                                "1")))))))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""));
    }

    @Test
    void getReturns200WithETag() throws Exception {
        when(service.findById(docId)).thenReturn(sampleResponse);

        mvc.perform(get(
                "/api/companies/{companyId}/eta/receipts/{docId}",
                companyId, docId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.receiptNumber").value("REC-001"));
    }

    @Test
    void putWithValidIfMatchReturns200() throws Exception {
        EtaReceiptResponse updated = new EtaReceiptResponse(
                docId, companyId, null, "REC-001",
                EtaReceiptDocumentType.r, "1.2",
                OffsetDateTime.now(), Map.of(), null,
                "POS-001", "CASH", "EGP",
                new BigDecimal("100.00000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100.00000"),
                new BigDecimal("114.00000"),
                null, DocumentState.DRAFT, 1,
                null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(),
                Collections.emptyList());
        when(service.update(eq(docId), any(), eq(0)))
                .thenReturn(updated);

        mvc.perform(put(
                "/api/companies/{companyId}/eta/receipts/{docId}",
                companyId, docId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiptNumber", "REC-001",
                                        "documentType", "r",
                                        "issueDatetime",
                                                "2026-05-13T14:30:00+02:00",
                                        "sellerData", Map.of(),
                                        "currency", "EGP",
                                        "lines", List.of(Map.of(
                                                "itemType", "EGS",
                                                "itemCode", "EGS-001",
                                                "description", "Updated",
                                                "unitType", "EA",
                                                "quantity", "2.00000",
                                                "unitValue", Map.of(
                                                        "currencySold", "EGP",
                                                        "amountEGP", "50",
                                                        "amountSold", "50",
                                                        "currencyExchangeRate",
                                                                "1")))))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    void putWithStaleIfMatchReturns409() throws Exception {
        when(service.update(eq(docId), any(), eq(0)))
                .thenThrow(new OptimisticLockConflictException("Conflict",
                        0, 1, sampleResponse));

        mvc.perform(put(
                "/api/companies/{companyId}/eta/receipts/{docId}",
                companyId, docId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiptNumber", "REC-001",
                                        "documentType", "r",
                                        "issueDatetime",
                                                "2026-05-13T14:30:00+02:00",
                                        "sellerData", Map.of(),
                                        "currency", "EGP",
                                        "lines", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void deleteDraftReturns204() throws Exception {
        mvc.perform(delete(
                "/api/companies/{companyId}/eta/receipts/{docId}",
                companyId, docId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNonDraftReturns409() throws Exception {
        org.mockito.Mockito.doThrow(
                new DocumentNotDraftException("Not draft", "SUBMITTING"))
                .when(service).delete(docId);

        mvc.perform(delete(
                "/api/companies/{companyId}/eta/receipts/{docId}",
                companyId, docId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DOCUMENT_NOT_DRAFT"));
    }

    @Test
    void createCancellationWithoutOriginalReturns400() throws Exception {
        when(service.create(any()))
                .thenThrow(new MissingOriginalDocumentException(
                        "cr requires original", null, "cr"));

        mvc.perform(post("/api/companies/{companyId}/eta/receipts",
                companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiptNumber", "REC-CR-001",
                                        "documentType", "cr",
                                        "issueDatetime",
                                                "2026-05-13T15:00:00+02:00",
                                        "sellerData", Map.of(),
                                        "currency", "EGP",
                                        "lines", List.of(Map.of(
                                                "itemType", "EGS",
                                                "itemCode", "EGS-001",
                                                "description", "Cancel",
                                                "unitType", "EA",
                                                "quantity", "1",
                                                "unitValue", Map.of(
                                                        "currencySold", "EGP",
                                                        "amountEGP", "50",
                                                        "amountSold", "50",
                                                        "currencyExchangeRate",
                                                                "1")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MISSING_ORIGINAL_DOCUMENT"));
    }

    @Test
    void createWithMissingUnitValueKeyReturns400() throws Exception {
        when(service.create(any()))
                .thenThrow(new InvalidUnitValueException(
                        "Missing key", "unitValue",
                        List.of("amountEGP")));

        mvc.perform(post("/api/companies/{companyId}/eta/receipts",
                companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("receiptNumber", "REC-002",
                                        "documentType", "r",
                                        "issueDatetime",
                                                "2026-05-13T14:30:00+02:00",
                                        "sellerData", Map.of(),
                                        "currency", "EGP",
                                        "lines", List.of(Map.of(
                                                "itemType", "EGS",
                                                "itemCode", "EGS-001",
                                                "description", "Bad",
                                                "unitType", "EA",
                                                "quantity", "1",
                                                "unitValue", Map.of(
                                                        "currencySold",
                                                                "EGP")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_UNIT_VALUE"));
    }

    @Test
    void listWithReceiptTypeFilter() throws Exception {
        Page<EtaReceiptResponse> page = new PageImpl<>(
                List.of(sampleResponse), PageRequest.of(0, 50), 1);
        when(service.list(any(), any(), eq("r"), any(), any(),
                anyInt(), anyInt())).thenReturn(page);

        mvc.perform(get("/api/companies/{companyId}/eta/receipts",
                companyId)
                        .param("receiptType", "r"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].documentType").value("r"));
    }
}
