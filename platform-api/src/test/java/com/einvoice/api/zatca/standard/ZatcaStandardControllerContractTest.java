package com.einvoice.api.zatca.standard;

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

import com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper.ZatcaStandardLineResponse;
import com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper.ZatcaStandardResponse;
import com.einvoice.api.zatca.standard.service.ZatcaStandardService;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
class ZatcaStandardControllerContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ZatcaStandardService service;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID companyId;
    private UUID docId;
    private ZatcaStandardResponse sampleResponse;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        docId = UUID.randomUUID();
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), companyId, (short) 5,
                "ZATCA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
                true, System.currentTimeMillis(), "jti"));
        sampleResponse = new ZatcaStandardResponse(
                docId, companyId, null,
                "STD-001", "388", "0100000",
                LocalDate.of(2026, 5, 19), LocalTime.of(14, 30),
                null, null,
                Map.of("partyName", "Seller"), Map.of("partyName", "Buyer"),
                "SAR", "SAR",
                new BigDecimal("300.00"), BigDecimal.ZERO,
                new BigDecimal("300.00"), new BigDecimal("45.00"),
                new BigDecimal("345.00"), BigDecimal.ZERO,
                new BigDecimal("345.00"),
                null, null, null, null,
                null, null, null,
                DocumentState.DRAFT, 0L,
                null, null, OffsetDateTime.now(), OffsetDateTime.now(),
                Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listReturnsPagedResults() throws Exception {
        Page<ZatcaStandardResponse> page = new PageImpl<>(
                List.of(sampleResponse),
                PageRequest.of(0, 50), 1);
        when(service.list(any(), any(), any(), any(), anyInt(),
                anyInt())).thenReturn(page);

        mvc.perform(get("/api/companies/{companyId}/zatca/standard",
                        companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].invoiceNumber")
                        .value("STD-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReturnsDocumentWithETag() throws Exception {
        when(service.findById(docId)).thenReturn(sampleResponse);

        mvc.perform(get(
                        "/api/companies/{companyId}/zatca/standard/{docId}",
                        companyId, docId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.invoiceNumber").value("STD-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createReturns201WithETag() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse);

        mvc.perform(post("/api/companies/{companyId}/zatca/standard",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "invoiceNumber", "STD-001",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0100000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData",
                                                Map.of("partyName",
                                                        "Seller"),
                                        "buyerData",
                                                Map.of("partyName",
                                                        "Buyer"),
                                        "lines", List.of()))))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.invoiceNumber").value("STD-001"));
    }

    @Test
    void updateReturns200WithNewETag() throws Exception {
        ZatcaStandardResponse updated = new ZatcaStandardResponse(
                docId, companyId, null, "STD-001-UPDATED", "388",
                "0100000",
                LocalDate.of(2026, 5, 19), LocalTime.of(14, 30),
                null, null,
                Map.of(), Map.of(), "SAR", "SAR",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                null, null, null, null, null, null, null,
                DocumentState.DRAFT, 1L,
                null, null, OffsetDateTime.now(), OffsetDateTime.now(),
                Collections.emptyList());
        when(service.update(eq(docId), any(), eq(0L)))
                .thenReturn(updated);

        mvc.perform(put(
                        "/api/companies/{companyId}/zatca/standard/{docId}",
                        companyId, docId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "invoiceNumber", "STD-001-UPDATED",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0100000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData", Map.of(),
                                        "buyerData", Map.of(),
                                        "lines", List.of()))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(
                        jsonPath("$.invoiceNumber").value("STD-001-UPDATED"));
    }

    @Test
    void deleteDraftReturns204() throws Exception {
        mvc.perform(delete(
                        "/api/companies/{companyId}/zatca/standard/{docId}",
                        companyId, docId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNonDraftReturns409() throws Exception {
        org.mockito.Mockito.doThrow(new DocumentNotDraftException(
                        "Not draft", "SUBMITTED"))
                .when(service).delete(docId);

        mvc.perform(delete(
                        "/api/companies/{companyId}/zatca/standard/{docId}",
                        companyId, docId))
                .andExpect(status().isConflict());
    }

    @Test
    void optimisticLockConflictReturns409() throws Exception {
        when(service.update(eq(docId), any(), any()))
                .thenThrow(new OptimisticLockConflictException(
                        "Conflict", 0, 1, sampleResponse));

        mvc.perform(put(
                        "/api/companies/{companyId}/zatca/standard/{docId}",
                        companyId, docId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void cloneAsDraftReturns201() throws Exception {
        when(service.cloneAsDraft(eq(docId), eq("STD-NEW")))
                .thenReturn(sampleResponse);

        mvc.perform(post("/api/companies/{companyId}"
                        + "/zatca/standard/{docId}/clone-as-draft",
                        companyId, docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newInvoiceNumber\":\"STD-NEW\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").exists());
    }

    @Test
    void missingBuyerReturns400() throws Exception {
        when(service.create(any())).thenThrow(
                new com.einvoice.core.error.MissingBuyerForStandardException(
                        "Buyer data is required for Standard documents"));

        mvc.perform(post("/api/companies/{companyId}/zatca/standard",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "STD-002",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0100000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData", Map.of("partyName", "S"),
                                        "buyerData", Map.of(),
                                        "lines", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MISSING_BUYER_FOR_STANDARD"));
    }

    @Test
    void vatExemptionReasonReturns400() throws Exception {
        when(service.create(any())).thenThrow(
                new com.einvoice.core.error.VatExemptionReasonRequiredException(
                        "VAT exemption reason required for category E", "E"));

        mvc.perform(post("/api/companies/{companyId}/zatca/standard",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "STD-003",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0100000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData", Map.of("partyName", "S"),
                                        "buyerData", Map.of("partyName", "B"),
                                        "lines", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAT_EXEMPTION_REASON_REQUIRED"));
    }

    @Test
    void duplicateStandardNumberReturns409() throws Exception {
        when(service.create(any())).thenThrow(
                new com.einvoice.core.error.DuplicateStandardNumberException(
                        "Duplicate standard number: STD-DUP",
                        companyId, "STD-DUP"));

        mvc.perform(post("/api/companies/{companyId}/zatca/standard",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "STD-DUP",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0100000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData", Map.of("partyName", "S"),
                                        "buyerData", Map.of("partyName", "B"),
                                        "lines", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_STANDARD_NUMBER"));
    }
}
