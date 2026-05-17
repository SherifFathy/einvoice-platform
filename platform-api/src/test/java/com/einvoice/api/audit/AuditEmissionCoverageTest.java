package com.einvoice.api.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.eta.engine.EtaAuthorityEngine;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditEmissionCoverageTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("einvoice_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EtaAuthorityEngine engine;

    private UUID companyId;
    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Audit Co").nameAr("شركة")
                .taxNumber("AC001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Audit Admin")
                .email("admin@audit-test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        userId = user.getId();
        token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", (short) 2, companyId,
                TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute(
                "ALTER TABLE invoice_artifacts DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "TRUNCATE TABLE invoice_artifacts, "
                        + "submission_attempts, audit_logs");
        jdbcTemplate.execute(
                "ALTER TABLE invoice_artifacts ENABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
        jdbcTemplate.update(
                "DELETE FROM eta_invoice_line_taxes "
                        + "WHERE line_id IN ("
                        + "SELECT l.id FROM eta_invoice_lines l "
                        + "JOIN eta_invoice_headers h "
                        + "ON l.header_id = h.id "
                        + "WHERE h.company_id = ?)",
                companyId);
        jdbcTemplate.update(
                "DELETE FROM eta_invoice_lines "
                        + "WHERE header_id IN ("
                        + "SELECT id FROM eta_invoice_headers "
                        + "WHERE company_id = ?)",
                companyId);
        jdbcTemplate.update(
                "DELETE FROM eta_invoice_headers "
                        + "WHERE company_id = ?", companyId);
        jdbcTemplate.update(
                "DELETE FROM eta_receipt_line_taxes "
                        + "WHERE line_id IN ("
                        + "SELECT l.id FROM eta_receipt_lines l "
                        + "JOIN eta_receipt_headers h "
                        + "ON l.header_id = h.id "
                        + "WHERE h.company_id = ?)",
                companyId);
        jdbcTemplate.update(
                "DELETE FROM eta_receipt_lines "
                        + "WHERE header_id IN ("
                        + "SELECT id FROM eta_receipt_headers "
                        + "WHERE company_id = ?)",
                companyId);
        jdbcTemplate.update(
                "DELETE FROM eta_receipt_headers "
                        + "WHERE company_id = ?", companyId);
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private int countAudit(String action, String entityId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs "
                        + "WHERE action = ? AND entity_id = ?",
                Integer.class, action, entityId);
    }

    private UUID auditUserId(String action, String entityId) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM audit_logs "
                        + "WHERE action = ? AND entity_id = ? "
                        + "LIMIT 1",
                UUID.class, action, entityId);
    }

    private void assertAuditCreated(String action, String entityId,
            int before) {
        int after = countAudit(action, entityId);
        assertEquals(before + 1, after,
                "Expected one new audit entry for action=" + action);
        assertEquals(userId, auditUserId(action, entityId),
                "Audit entry should carry the actor's userId");
    }

    private String createInvoiceDraft(String number) throws Exception {
        String body = """
                {"invoiceNumber":"%s","documentType":"i",
                 "issueDatetime":"2026-05-13T10:00:00+02:00",
                 "sellerData":{"type":"B","name":"Seller"},
                 "buyerData":{"type":"P","name":"Buyer"},
                 "currency":"EGP",
                 "lines":[{
                   "lineNumber":1,"itemType":"GS1","itemCode":"ITM-1",
                   "description":"Test","unitType":"EA","quantity":1,
                   "unitValue":{"currencySold":"EGP","amountEGP":100,"amountSold":100,"currencyExchangeRate":1},
                   "salesTotal":100,"discountAmount":0,"itemsDiscount":0,
                   "valueDifference":0,"totalTaxableFees":0,"netTotal":100,
                   "taxAmount":0,"total":100,
                   "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
                 }]}""".formatted(number);
        String resp = mockMvc.perform(
                post("/api/companies/{companyId}/eta/invoices",
                        companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asText();
    }

    private String createReceiptDraft(String number) throws Exception {
        String body = """
                {"receiptNumber":"%s","documentType":"r",
                 "issueDatetime":"2026-05-13T14:30:00+02:00",
                 "sellerData":{"type":"B","name":"Seller"},
                 "currency":"EGP",
                 "totalSalesAmount":50,"totalDiscountAmount":0,
                 "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                 "netAmount":50,"totalAmount":57,
                 "lines":[{
                   "lineNumber":1,"itemType":"EGS","itemCode":"EGS-1",
                   "description":"Test","unitType":"EA","quantity":2,
                   "unitValue":{"currencySold":"EGP","amountEGP":25,"amountSold":25,"currencyExchangeRate":1},
                   "salesTotal":50,"discountAmount":0,"itemsDiscount":0,
                   "valueDifference":0,"totalTaxableFees":0,"netTotal":50,
                   "taxAmount":7,"total":57,
                   "taxes":[{"taxType":"T1","taxRate":14,"taxAmount":7}]
                 }]}""".formatted(number);
        String resp = mockMvc.perform(
                post("/api/companies/{companyId}/eta/receipts",
                        companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asText();
    }

    private void mockSubmitSuccess() {
        SignedPayload sp = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep =
                new EtaAuthorityEngine.Preparation(
                        sp, "clientId", "clientSecret",
                        "https://token.url");
        when(engine.prepareSubmission(any(), any(), anyShort()))
                .thenReturn(prep);
        when(engine.httpSubmit(any(), any(), anyShort(), any()))
                .thenReturn(new AuthorityResponse(true, "SUCCESS",
                        "eta-uuid-001", "long-1", "sub-001",
                        200, null,
                        "{\"uuid\":\"eta-uuid-001\"}"));
    }

    private void mockReceiptSubmitSuccess() {
        SignedPayload sp = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep =
                new EtaAuthorityEngine.Preparation(
                        sp, "clientId", "clientSecret",
                        "https://token.url");
        when(engine.prepareReceiptSubmission(any(), any(), anyShort()))
                .thenReturn(prep);
        when(engine.httpSubmitReceipt(any(), any(), anyShort(), any()))
                .thenReturn(new AuthorityResponse(true, "SUCCESS",
                        "eta-rcpt-uuid-001", null, "sub-rcpt-001",
                        200, null,
                        "{\"uuid\":\"eta-rcpt-uuid-001\"}"));
    }

    private void mockSubmitAmbiguous() {
        SignedPayload sp = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep =
                new EtaAuthorityEngine.Preparation(
                        sp, "clientId", "clientSecret",
                        "https://token.url");
        when(engine.prepareSubmission(any(), any(), anyShort()))
                .thenReturn(prep);
        when(engine.httpSubmit(any(), any(), anyShort(), any()))
                .thenThrow(new RuntimeException("Connection timeout"));
    }

    private void mockReceiptSubmitAmbiguous() {
        SignedPayload sp = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep =
                new EtaAuthorityEngine.Preparation(
                        sp, "clientId", "clientSecret",
                        "https://token.url");
        when(engine.prepareReceiptSubmission(any(), any(), anyShort()))
                .thenReturn(prep);
        when(engine.httpSubmitReceipt(any(), any(), anyShort(), any()))
                .thenThrow(new RuntimeException("Connection timeout"));
    }

    private void mockSubmitRejected() {
        SignedPayload sp = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep =
                new EtaAuthorityEngine.Preparation(
                        sp, "clientId", "clientSecret",
                        "https://token.url");
        when(engine.prepareSubmission(any(), any(), anyShort()))
                .thenReturn(prep);
        when(engine.httpSubmit(any(), any(), anyShort(), any()))
                .thenReturn(new AuthorityResponse(false, "REJECTED",
                        null, null, null,
                        400, "Invalid document",
                        "{\"error\":\"rejected\"}"));
    }

    private void mockReceiptSubmitRejected() {
        SignedPayload sp = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep =
                new EtaAuthorityEngine.Preparation(
                        sp, "clientId", "clientSecret",
                        "https://token.url");
        when(engine.prepareReceiptSubmission(any(), any(), anyShort()))
                .thenReturn(prep);
        when(engine.httpSubmitReceipt(any(), any(), anyShort(), any()))
                .thenReturn(new AuthorityResponse(false, "REJECTED",
                        null, null, null,
                        400, "Invalid receipt",
                        "{\"error\":\"rejected\"}"));
    }

    private void mockCancelSuccess() {
        when(engine.cancel(any())).thenReturn(
                new AuthorityResponse(true, "SUCCESS",
                        "eta-uuid-001", "long-1", "sub-cancel-001",
                        200, null,
                        "{\"uuid\":\"eta-uuid-001\"}"));
    }

    private void mockCheckStatusSuccess() {
        when(engine.checkStatus(any())).thenReturn(
                new AuthorityResponse(true, "SUCCESS",
                        "eta-uuid-001", "long-1", "sub-001",
                        200, null,
                        "{\"uuid\":\"eta-uuid-001\"}"));
    }

    private void mockReceiptCheckStatusSuccess() {
        when(engine.checkStatus(any())).thenReturn(
                new AuthorityResponse(true, "SUCCESS",
                        "eta-rcpt-uuid-001", null, "sub-rcpt-001",
                        200, null,
                        "{\"uuid\":\"eta-rcpt-uuid-001\"}"));
    }

    private void submitInvoiceSuccessfully(String docId)
            throws Exception {
        mockSubmitSuccess();
        mockMvc.perform(
                post("/api/companies/{companyId}/eta/invoices/"
                        + "{docId}/submit", companyId, docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void submitReceiptSuccessfully(String docId)
            throws Exception {
        mockReceiptSubmitSuccess();
        mockMvc.perform(
                post("/api/companies/{companyId}/eta/receipts/"
                        + "{docId}/submit", companyId, docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void submitInvoiceAmbiguous(String docId)
            throws Exception {
        mockSubmitAmbiguous();
        mockMvc.perform(
                post("/api/companies/{companyId}/eta/invoices/"
                        + "{docId}/submit", companyId, docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void submitReceiptAmbiguous(String docId)
            throws Exception {
        mockReceiptSubmitAmbiguous();
        mockMvc.perform(
                post("/api/companies/{companyId}/eta/receipts/"
                        + "{docId}/submit", companyId, docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void submitInvoiceRejected(String docId)
            throws Exception {
        mockSubmitRejected();
        mockMvc.perform(
                post("/api/companies/{companyId}/eta/invoices/"
                        + "{docId}/submit", companyId, docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void submitReceiptRejected(String docId)
            throws Exception {
        mockReceiptSubmitRejected();
        mockMvc.perform(
                post("/api/companies/{companyId}/eta/receipts/"
                        + "{docId}/submit", companyId, docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Nested
    class InvoiceActions {

        @Test
        void create_emitsCreateInvoiceAudit() throws Exception {
            String docId = createInvoiceDraft("INV-AUD-C01");
            assertEquals(1, countAudit("CREATE_INVOICE", docId),
                    "Expected exactly one CREATE_INVOICE audit row");
            assertEquals(userId,
                    auditUserId("CREATE_INVOICE", docId),
                    "Audit entry should carry the actor's userId");
        }

        @Test
        void edit_emitsEditInvoiceAudit() throws Exception {
            String docId = createInvoiceDraft("INV-AUD-E01");
            int before = countAudit("EDIT_INVOICE", docId);

            String editBody = """
                    {"invoiceNumber":"INV-AUD-E01","documentType":"i",
                     "issueDatetime":"2026-05-13T10:00:00+02:00",
                     "sellerData":{"type":"B","name":"Updated Seller"},
                     "buyerData":{"type":"P","name":"Buyer"},
                     "currency":"EGP",
                     "lines":[{
                       "lineNumber":1,"itemType":"GS1","itemCode":"ITM-1",
                       "description":"Test","unitType":"EA","quantity":1,
                       "unitValue":{"currencySold":"EGP","amountEGP":100,"amountSold":100,"currencyExchangeRate":1},
                       "salesTotal":100,"discountAmount":0,"itemsDiscount":0,
                       "valueDifference":0,"totalTaxableFees":0,"netTotal":100,
                       "taxAmount":0,"total":100,
                       "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
                     }]}""";
            mockMvc.perform(
                    put("/api/companies/{companyId}/eta/invoices/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody))
                    .andExpect(status().isOk());

            assertAuditCreated("EDIT_INVOICE", docId, before);
        }

        @Test
        void delete_emitsDeleteInvoiceAudit() throws Exception {
            String docId = createInvoiceDraft("INV-AUD-D01");
            int before = countAudit("DELETE_INVOICE", docId);

            mockMvc.perform(
                    delete("/api/companies/{companyId}"
                            + "/eta/invoices/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isNoContent());

            assertAuditCreated("DELETE_INVOICE", docId, before);
        }

        @Test
        void submit_emitsSubmitInvoiceAudit() throws Exception {
            String docId = createInvoiceDraft("INV-AUD-S01");
            int before = countAudit("SUBMIT_INVOICE", docId);

            submitInvoiceSuccessfully(docId);

            assertAuditCreated("SUBMIT_INVOICE", docId, before);
        }

        @Test
        void cancel_emitsCancelInvoiceAudit() throws Exception {
            String docId = createInvoiceDraft("INV-AUD-CA01");
            submitInvoiceSuccessfully(docId);
            int before = countAudit("CANCEL_INVOICE", docId);

            mockCancelSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}/eta/invoices/"
                            + "{docId}/cancel", companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"reason\":\"test cancel\"}"))
                    .andExpect(status().isOk());

            assertAuditCreated("CANCEL_INVOICE", docId, before);
        }

        @Test
        void retry_emitsSubmitInvoiceAudit() throws Exception {
            String docId = createInvoiceDraft("INV-AUD-R01");
            submitInvoiceAmbiguous(docId);
            int before = countAudit("SUBMIT_INVOICE", docId);

            mockSubmitSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}/eta/invoices/"
                            + "{docId}/retry", companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            assertAuditCreated("SUBMIT_INVOICE", docId, before);
        }

        @Test
        void checkStatus_emitsCheckStatusInvoiceAudit()
                throws Exception {
            String docId = createInvoiceDraft("INV-AUD-CS01");
            submitInvoiceSuccessfully(docId);

            jdbcTemplate.update(
                    "UPDATE eta_invoice_headers "
                            + "SET state = 'IN_REVIEW' "
                            + "WHERE id = ?::uuid",
                    docId);

            int before = countAudit("CHECK_STATUS_INVOICE", docId);

            mockCheckStatusSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}/eta/invoices/"
                            + "check-status", companyId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"documentIds\":[\""
                                    + docId + "\"]}"))
                    .andExpect(status().isOk());

            int after = countAudit("CHECK_STATUS_INVOICE", docId);
            assertEquals(before + 1, after,
                    "Expected one CHECK_STATUS_INVOICE audit entry");
        }

        @Test
        void cloneAsDraft_emitsCloneToNewDraftAudit()
                throws Exception {
            String docId = createInvoiceDraft("INV-AUD-CL01");
            submitInvoiceRejected(docId);

            int before = countAudit("CLONE_TO_NEW_DRAFT", null);
            int specificBefore = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE action = 'CLONE_TO_NEW_DRAFT' "
                            + "AND payload_after::text "
                            + "LIKE '%INV-AUD-CL02%'",
                    Integer.class);

            mockMvc.perform(
                    post("/api/companies/{companyId}/eta/invoices/"
                            + "{docId}/clone-as-draft",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"invoiceNumber\":"
                                    + "\"INV-AUD-CL02\"}"))
                    .andExpect(status().isCreated());

            int specificAfter = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE action = 'CLONE_TO_NEW_DRAFT' "
                            + "AND payload_after::text "
                            + "LIKE '%INV-AUD-CL02%'",
                    Integer.class);
            assertEquals(specificBefore + 1, specificAfter,
                    "Expected one CLONE_TO_NEW_DRAFT audit for "
                            + "the new invoice number");

            UUID cloneActionUserId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM audit_logs "
                            + "WHERE action = 'CLONE_TO_NEW_DRAFT' "
                            + "AND payload_after::text "
                            + "LIKE '%INV-AUD-CL02%' "
                            + "LIMIT 1",
                    UUID.class);
            assertEquals(userId, cloneActionUserId);
        }

        @Test
        void overwriteOnConflict_emitsEditInvoiceAudit()
                throws Exception {
            String docId = createInvoiceDraft("INV-AUD-OW01");

            String editBody = """
                    {"invoiceNumber":"INV-AUD-OW01","documentType":"i",
                     "issueDatetime":"2026-05-13T10:00:00+02:00",
                     "sellerData":{"type":"B","name":"First Edit"},
                     "buyerData":{"type":"P","name":"Buyer"},
                     "currency":"EGP",
                     "lines":[{
                       "lineNumber":1,"itemType":"GS1","itemCode":"ITM-1",
                       "description":"Test","unitType":"EA","quantity":1,
                       "unitValue":{"currencySold":"EGP","amountEGP":100,"amountSold":100,"currencyExchangeRate":1},
                       "salesTotal":100,"discountAmount":0,"itemsDiscount":0,
                       "valueDifference":0,"totalTaxableFees":0,"netTotal":100,
                       "taxAmount":0,"total":100,
                       "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
                     }]}""";
            mockMvc.perform(
                    put("/api/companies/{companyId}/eta/invoices/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody))
                    .andExpect(status().isOk());

            int editCountBefore = countAudit("EDIT_INVOICE", docId);

            mockMvc.perform(
                    put("/api/companies/{companyId}/eta/invoices/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody))
                    .andExpect(status().isConflict());

            assertEquals(editCountBefore,
                    countAudit("EDIT_INVOICE", docId),
                    "Conflict response must NOT produce an audit entry");

            String overwriteBody = """
                    {"invoiceNumber":"INV-AUD-OW01","documentType":"i",
                     "issueDatetime":"2026-05-13T10:00:00+02:00",
                     "sellerData":{"type":"B","name":"Overwritten"},
                     "buyerData":{"type":"P","name":"Buyer"},
                     "currency":"EGP",
                     "lines":[{
                       "lineNumber":1,"itemType":"GS1","itemCode":"ITM-1",
                       "description":"Test","unitType":"EA","quantity":1,
                       "unitValue":{"currencySold":"EGP","amountEGP":100,"amountSold":100,"currencyExchangeRate":1},
                       "salesTotal":100,"discountAmount":0,"itemsDiscount":0,
                       "valueDifference":0,"totalTaxableFees":0,"netTotal":100,
                       "taxAmount":0,"total":100,
                       "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
                     }]}""";
            mockMvc.perform(
                    put("/api/companies/{companyId}/eta/invoices/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"1\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(overwriteBody))
                    .andExpect(status().isOk());

            assertAuditCreated("EDIT_INVOICE", docId,
                    editCountBefore);
        }
    }

    @Nested
    class ReceiptActions {

        @Test
        void create_emitsCreateReceiptAudit() throws Exception {
            String docId = createReceiptDraft("REC-AUD-C01");
            assertEquals(1, countAudit("CREATE_RECEIPT", docId),
                    "Expected exactly one CREATE_RECEIPT audit row");
            assertEquals(userId,
                    auditUserId("CREATE_RECEIPT", docId),
                    "Audit entry should carry the actor's userId");
        }

        @Test
        void edit_emitsEditReceiptAudit() throws Exception {
            String docId = createReceiptDraft("REC-AUD-E01");
            int before = countAudit("EDIT_RECEIPT", docId);

            String editBody = """
                    {"receiptNumber":"REC-AUD-E01","documentType":"r",
                     "issueDatetime":"2026-05-13T14:30:00+02:00",
                     "sellerData":{"type":"B","name":"Updated Seller"},
                     "currency":"EGP",
                     "totalSalesAmount":50,"totalDiscountAmount":0,
                     "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                     "netAmount":50,"totalAmount":57,
                     "lines":[{
                       "lineNumber":1,"itemType":"EGS","itemCode":"EGS-1",
                       "description":"Test","unitType":"EA","quantity":2,
                       "unitValue":{"currencySold":"EGP","amountEGP":25,"amountSold":25,"currencyExchangeRate":1},
                       "salesTotal":50,"discountAmount":0,"itemsDiscount":0,
                       "valueDifference":0,"totalTaxableFees":0,"netTotal":50,
                       "taxAmount":7,"total":57,
                       "taxes":[{"taxType":"T1","taxRate":14,"taxAmount":7}]
                     }]}""";
            mockMvc.perform(
                    put("/api/companies/{companyId}/eta/receipts/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody))
                    .andExpect(status().isOk());

            assertAuditCreated("EDIT_RECEIPT", docId, before);
        }

        @Test
        void delete_emitsDeleteReceiptAudit() throws Exception {
            String docId = createReceiptDraft("REC-AUD-D01");
            int before = countAudit("DELETE_RECEIPT", docId);

            mockMvc.perform(
                    delete("/api/companies/{companyId}"
                            + "/eta/receipts/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isNoContent());

            assertAuditCreated("DELETE_RECEIPT", docId, before);
        }

        @Test
        void submit_emitsSubmitReceiptAudit() throws Exception {
            String docId = createReceiptDraft("REC-AUD-S01");
            int before = countAudit("SUBMIT_RECEIPT", docId);

            submitReceiptSuccessfully(docId);

            assertAuditCreated("SUBMIT_RECEIPT", docId, before);
        }

        @Test
        void cancel_emitsCancelReceiptAudit() throws Exception {
            String docId = createReceiptDraft("REC-AUD-CA01");
            submitReceiptSuccessfully(docId);
            int before = countAudit("CANCEL_RECEIPT", docId);

            when(engine.cancel(any())).thenReturn(
                    new AuthorityResponse(true, "SUCCESS",
                            "eta-rcpt-uuid-001", null,
                            "sub-rcpt-cancel-001",
                            200, null,
                            "{\"uuid\":\"eta-rcpt-uuid-001\"}"));
            mockMvc.perform(
                    post("/api/companies/{companyId}/eta/receipts/"
                            + "{docId}/cancel", companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"reason\":\"test cancel\"}"))
                    .andExpect(status().isOk());

            assertAuditCreated("CANCEL_RECEIPT", docId, before);
        }

        @Test
        void retry_emitsSubmitReceiptAudit() throws Exception {
            String docId = createReceiptDraft("REC-AUD-R01");
            submitReceiptAmbiguous(docId);
            int before = countAudit("SUBMIT_RECEIPT", docId);

            mockReceiptSubmitSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}/eta/receipts/"
                            + "{docId}/retry", companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            assertAuditCreated("SUBMIT_RECEIPT", docId, before);
        }

        @Test
        void checkStatus_emitsCheckStatusReceiptAudit()
                throws Exception {
            String docId = createReceiptDraft("REC-AUD-CS01");
            submitReceiptSuccessfully(docId);

            jdbcTemplate.update(
                    "UPDATE eta_receipt_headers "
                            + "SET state = 'IN_REVIEW' "
                            + "WHERE id = ?::uuid",
                    docId);

            int before = countAudit("CHECK_STATUS_RECEIPT", docId);

            mockReceiptCheckStatusSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}/eta/receipts/"
                            + "check-status", companyId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"documentIds\":[\""
                                    + docId + "\"]}"))
                    .andExpect(status().isOk());

            int after = countAudit("CHECK_STATUS_RECEIPT", docId);
            assertEquals(before + 1, after,
                    "Expected one CHECK_STATUS_RECEIPT audit entry");
        }

        @Test
        void cloneAsDraft_emitsCloneToNewDraftAudit()
                throws Exception {
            String docId = createReceiptDraft("REC-AUD-CL01");
            submitReceiptRejected(docId);

            int specificBefore = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE action = 'CLONE_TO_NEW_DRAFT' "
                            + "AND entity_type = 'ETA_RECEIPT' "
                            + "AND payload_after::text "
                            + "LIKE '%REC-AUD-CL02%'",
                    Integer.class);

            mockMvc.perform(
                    post("/api/companies/{companyId}/eta/receipts/"
                            + "{docId}/clone-as-draft",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"receiptNumber\":"
                                    + "\"REC-AUD-CL02\"}"))
                    .andExpect(status().isCreated());

            int specificAfter = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE action = 'CLONE_TO_NEW_DRAFT' "
                            + "AND entity_type = 'ETA_RECEIPT' "
                            + "AND payload_after::text "
                            + "LIKE '%REC-AUD-CL02%'",
                    Integer.class);
            assertEquals(specificBefore + 1, specificAfter,
                    "Expected one CLONE_TO_NEW_DRAFT audit for "
                            + "the new receipt number");

            UUID cloneActionUserId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM audit_logs "
                            + "WHERE action = 'CLONE_TO_NEW_DRAFT' "
                            + "AND entity_type = 'ETA_RECEIPT' "
                            + "AND payload_after::text "
                            + "LIKE '%REC-AUD-CL02%' "
                            + "LIMIT 1",
                    UUID.class);
            assertEquals(userId, cloneActionUserId);
        }

        @Test
        void overwriteOnConflict_emitsEditReceiptAudit()
                throws Exception {
            String docId = createReceiptDraft("REC-AUD-OW01");

            String editBody = """
                    {"receiptNumber":"REC-AUD-OW01","documentType":"r",
                     "issueDatetime":"2026-05-13T14:30:00+02:00",
                     "sellerData":{"type":"B","name":"First Edit"},
                     "currency":"EGP",
                     "totalSalesAmount":50,"totalDiscountAmount":0,
                     "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                     "netAmount":50,"totalAmount":57,
                     "lines":[{
                       "lineNumber":1,"itemType":"EGS","itemCode":"EGS-1",
                       "description":"Test","unitType":"EA","quantity":2,
                       "unitValue":{"currencySold":"EGP","amountEGP":25,"amountSold":25,"currencyExchangeRate":1},
                       "salesTotal":50,"discountAmount":0,"itemsDiscount":0,
                       "valueDifference":0,"totalTaxableFees":0,"netTotal":50,
                       "taxAmount":7,"total":57,
                       "taxes":[{"taxType":"T1","taxRate":14,"taxAmount":7}]
                     }]}""";
            mockMvc.perform(
                    put("/api/companies/{companyId}/eta/receipts/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody))
                    .andExpect(status().isOk());

            int editCountBefore = countAudit("EDIT_RECEIPT", docId);

            mockMvc.perform(
                    put("/api/companies/{companyId}/eta/receipts/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody))
                    .andExpect(status().isConflict());

            assertEquals(editCountBefore,
                    countAudit("EDIT_RECEIPT", docId),
                    "Conflict response must NOT produce an audit entry");

            String overwriteBody = """
                    {"receiptNumber":"REC-AUD-OW01","documentType":"r",
                     "issueDatetime":"2026-05-13T14:30:00+02:00",
                     "sellerData":{"type":"B","name":"Overwritten"},
                     "currency":"EGP",
                     "totalSalesAmount":50,"totalDiscountAmount":0,
                     "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                     "netAmount":50,"totalAmount":57,
                     "lines":[{
                       "lineNumber":1,"itemType":"EGS","itemCode":"EGS-1",
                       "description":"Test","unitType":"EA","quantity":2,
                       "unitValue":{"currencySold":"EGP","amountEGP":25,"amountSold":25,"currencyExchangeRate":1},
                       "salesTotal":50,"discountAmount":0,"itemsDiscount":0,
                       "valueDifference":0,"totalTaxableFees":0,"netTotal":50,
                       "taxAmount":7,"total":57,
                       "taxes":[{"taxType":"T1","taxRate":14,"taxAmount":7}]
                     }]}""";
            mockMvc.perform(
                    put("/api/companies/{companyId}/eta/receipts/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"1\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(overwriteBody))
                    .andExpect(status().isOk());

            assertAuditCreated("EDIT_RECEIPT", docId,
                    editCountBefore);
        }
    }
}
