package com.einvoice.api.zatca.simplified;

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

import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper.ZatcaSimplifiedLineResponse;
import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper.ZatcaSimplifiedResponse;
import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedService;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.DuplicateSimplifiedNumberException;
import com.einvoice.core.error.InvalidSimplifiedTransactionTypeException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.core.error.UnauthorizedContextException;
import com.einvoice.core.error.VatExemptionReasonRequiredException;
import com.einvoice.core.error.WrongOriginalClassException;
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
class ZatcaSimplifiedControllerContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ZatcaSimplifiedService service;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID companyId;
    private UUID docId;
    private ZatcaSimplifiedResponse sampleResponse;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        docId = UUID.randomUUID();
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), companyId, (short) 5,
                "ZATCA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
                true, System.currentTimeMillis(), "jti"));
        sampleResponse = new ZatcaSimplifiedResponse(
                docId, companyId, null,
                "SIMP-001", "388", "0200000",
                "reporting:1.0", null, null, null, null,
                LocalDate.of(2026, 5, 19), LocalTime.of(14, 30),
                null, null,
                Map.of("partyName", "Seller"), null,
                null, "SA",
                "SAR", "SAR",
                new BigDecimal("150.00"), BigDecimal.ZERO,
                new BigDecimal("150.00"), new BigDecimal("22.50"),
                BigDecimal.ZERO,
                new BigDecimal("172.50"), BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("172.50"),
                null, null,
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
        Page<ZatcaSimplifiedResponse> page = new PageImpl<>(
                List.of(sampleResponse),
                PageRequest.of(0, 50), 1);
        when(service.list(any(), any(), any(), any(), anyInt(),
                anyInt())).thenReturn(page);

        mvc.perform(get("/api/companies/{companyId}/zatca/simplified",
                        companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].invoiceNumber")
                        .value("SIMP-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReturnsDocumentWithETag() throws Exception {
        when(service.findById(docId)).thenReturn(sampleResponse);

        mvc.perform(get(
                        "/api/companies/{companyId}"
                                + "/zatca/simplified/{docId}",
                        companyId, docId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.invoiceNumber").value("SIMP-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createReturns201WithETag_noBuyer() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse);

        mvc.perform(post(
                        "/api/companies/{companyId}/zatca/simplified",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "invoiceNumber", "SIMP-001",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0200000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData",
                                                Map.of("partyName",
                                                        "Seller"),
                                        "lines", List.of()))))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(
                        jsonPath("$.invoiceNumber").value("SIMP-001"));
    }

    @Test
    void createWithEmptyBuyerSucceeds() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse);

        mvc.perform(post(
                        "/api/companies/{companyId}/zatca/simplified",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "invoiceNumber", "SIMP-001",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0200000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData",
                                                Map.of("partyName",
                                                        "Seller"),
                                        "buyerData", Map.of(),
                                        "lines", List.of()))))
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturns200WithNewETag() throws Exception {
        ZatcaSimplifiedResponse updated =
                new ZatcaSimplifiedResponse(
                        docId, companyId, null, "SIMP-001-UPDATED",
                        "388", "0200000",
                        "reporting:1.0", null, null, null, null,
                        LocalDate.of(2026, 5, 19),
                        LocalTime.of(14, 30),
                        null, null,
                        Map.of(), null,
                        null, "SA",
                        "SAR", "SAR",
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null, null,
                        null, null, null, null,
                        null, null, null,
                        DocumentState.DRAFT, 1L,
                        null, null, OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        Collections.emptyList());
        when(service.update(eq(docId), any(), eq(0L)))
                .thenReturn(updated);

        mvc.perform(put(
                        "/api/companies/{companyId}"
                                + "/zatca/simplified/{docId}",
                        companyId, docId)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "invoiceNumber",
                                                "SIMP-001-UPDATED",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0200000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData", Map.of(),
                                        "lines", List.of()))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.invoiceNumber")
                        .value("SIMP-001-UPDATED"));
    }

    @Test
    void deleteDraftReturns204() throws Exception {
        mvc.perform(delete(
                        "/api/companies/{companyId}"
                                + "/zatca/simplified/{docId}",
                        companyId, docId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNonDraftReturns409() throws Exception {
        org.mockito.Mockito.doThrow(new DocumentNotDraftException(
                        "Not draft", "SUBMITTED"))
                .when(service).delete(docId);

        mvc.perform(delete(
                        "/api/companies/{companyId}"
                                + "/zatca/simplified/{docId}",
                        companyId, docId))
                .andExpect(status().isConflict());
    }

    @Test
    void optimisticLockConflictReturns409() throws Exception {
        when(service.update(eq(docId), any(), any()))
                .thenThrow(new OptimisticLockConflictException(
                        "Conflict", 0, 1, sampleResponse));

        mvc.perform(put(
                        "/api/companies/{companyId}"
                                + "/zatca/simplified/{docId}",
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
        when(service.cloneAsDraft(eq(docId), eq("SIMP-NEW")))
                .thenReturn(sampleResponse);

        mvc.perform(post("/api/companies/{companyId}"
                                + "/zatca/simplified/{docId}"
                                + "/clone-as-draft",
                        companyId, docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"newInvoiceNumber\":\"SIMP-NEW\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").exists());
    }

    @Test
    void non02PrefixTransactionTypeReturns400() throws Exception {
        when(service.create(any())).thenThrow(
                new InvalidSimplifiedTransactionTypeException(
                        "Simplified transaction type code must start "
                                + "with 02, but was: 0100000",
                        "0100000"));

        mvc.perform(post(
                        "/api/companies/{companyId}/zatca/simplified",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "SIMP-BAD",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0100000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData",
                                                Map.of("partyName", "S"),
                                        "lines", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_SIMPLIFIED_TRANSACTION_TYPE"));
    }

    @Test
    void vatExemptionReasonReturns400() throws Exception {
        when(service.create(any())).thenThrow(
                new VatExemptionReasonRequiredException(
                        "VAT exemption reason required for category E",
                        "E"));

        mvc.perform(post(
                        "/api/companies/{companyId}/zatca/simplified",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "SIMP-003",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0200000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData",
                                                Map.of("partyName", "S"),
                                        "lines", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAT_EXEMPTION_REASON_REQUIRED"));
    }

    @Test
    void duplicateSimplifiedNumberReturns409() throws Exception {
        when(service.create(any())).thenThrow(
                new DuplicateSimplifiedNumberException(
                        "Duplicate simplified number: SIMP-DUP",
                        companyId, "SIMP-DUP"));

        mvc.perform(post(
                        "/api/companies/{companyId}/zatca/simplified",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "SIMP-DUP",
                                        "invoiceTypeCode", "388",
                                        "transactionTypeCode", "0200000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData",
                                                Map.of("partyName", "S"),
                                        "lines", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_SIMPLIFIED_NUMBER"));
    }

    @Test
    void missingOriginalDocumentReturns400() throws Exception {
        when(service.create(any())).thenThrow(
                new MissingOriginalDocumentException(
                        "Credit note requires an original document",
                        docId, "ZATCA_SIMPLIFIED"));

        mvc.perform(post(
                        "/api/companies/{companyId}/zatca/simplified",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "SIMP-CN",
                                        "invoiceTypeCode", "381",
                                        "transactionTypeCode", "0200000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData",
                                                Map.of("partyName", "S"),
                                        "lines", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MISSING_ORIGINAL_DOCUMENT"));
    }

    @Test
    void wrongOriginalClassReturns400() throws Exception {
        when(service.create(any())).thenThrow(
                new WrongOriginalClassException(
                        "Original must be ZATCA_SIMPLIFIED",
                        "ZATCA_SIMPLIFIED", "ZATCA_STANDARD"));

        mvc.perform(post(
                        "/api/companies/{companyId}/zatca/simplified",
                        companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("invoiceNumber", "SIMP-CN",
                                        "invoiceTypeCode", "381",
                                        "transactionTypeCode", "0200000",
                                        "issueDate", "2026-05-19",
                                        "issueTime", "14:30:00",
                                        "sellerData",
                                                Map.of("partyName", "S"),
                                        "lines", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("WRONG_ORIGINAL_CLASS"))
                .andExpect(jsonPath("$.details.expectedClass")
                        .value("ZATCA_SIMPLIFIED"))
                .andExpect(jsonPath("$.details.actualClass")
                        .value("ZATCA_STANDARD"));
    }

    @Test
    void unauthorizedCompanyReturns401() throws Exception {
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), UUID.randomUUID(), (short) 5,
                "ZATCA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
                true, System.currentTimeMillis(), "jti"));
        UUID otherCompanyId = UUID.randomUUID();

        mvc.perform(get(
                        "/api/companies/{companyId}"
                                + "/zatca/simplified/{docId}",
                        otherCompanyId, docId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED_CONTEXT"));
    }

    @Test
    void cloneAsDraftDuplicateNumberReturns409() throws Exception {
        when(service.cloneAsDraft(eq(docId), eq("SIMP-DUP")))
                .thenThrow(new DuplicateSimplifiedNumberException(
                        "Duplicate simplified number: SIMP-DUP",
                        companyId, "SIMP-DUP"));

        mvc.perform(post("/api/companies/{companyId}"
                                + "/zatca/simplified/{docId}"
                                + "/clone-as-draft",
                        companyId, docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"newInvoiceNumber\":\"SIMP-DUP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DUPLICATE_SIMPLIFIED_NUMBER"));
    }
}
