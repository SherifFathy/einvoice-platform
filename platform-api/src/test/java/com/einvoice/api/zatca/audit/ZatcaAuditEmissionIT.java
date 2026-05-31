package com.einvoice.api.zatca.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ZatcaAuditEmissionIT {

    private static final Logger log =
            LoggerFactory.getLogger(ZatcaAuditEmissionIT.class);

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private ZatcaAuthorityEngine engine;

    private UUID companyId;
    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("ZATCA Audit Co").nameAr("تدقيق")
                .taxNumber("ZA001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Audit Admin")
                .email("zatca-audit@test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        userId = user.getId();

        jdbcTemplate.update(
                "INSERT INTO user_company_transaction_roles "
                        + "(user_id, company_id, authority_environment_id, "
                        + "transaction_type, role_name) "
                        + "VALUES (?, ?, 5, 'STANDARD', 'COMPANY_ADMIN')",
                userId, companyId);
        jdbcTemplate.update(
                "INSERT INTO user_company_transaction_roles "
                        + "(user_id, company_id, authority_environment_id, "
                        + "transaction_type, role_name) "
                        + "VALUES (?, ?, 5, 'SIMPLIFIED', 'COMPANY_ADMIN')",
                userId, companyId);

        jdbcTemplate.update(
                "INSERT INTO zatca_chain_state "
                        + "(company_id, authority_environment_id, "
                        + "invoice_counter, updated_at) "
                        + "VALUES (?, ?, 0, NOW())",
                companyId, (short) 5);

        token = jwtTokenProvider.createToken(
                userId, user.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5, companyId,
                TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        disableAppendOnlyTriggers();
        try {
            jdbcTemplate.execute(
                    "TRUNCATE TABLE invoice_artifacts, "
                            + "submission_attempts, audit_logs");
        } finally {
            enableAppendOnlyTriggers();
        }
        cleanupBusinessData();
    }

    private void disableAppendOnlyTriggers() {
        jdbcTemplate.execute(
                "ALTER TABLE invoice_artifacts DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
    }

    private void enableAppendOnlyTriggers() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE invoice_artifacts ENABLE TRIGGER ALL");
        } catch (Exception e) {
            log.warn("Failed to re-enable invoice_artifacts triggers", e);
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        } catch (Exception e) {
            log.warn("Failed to re-enable audit_logs triggers", e);
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
        } catch (Exception e) {
            log.warn("Failed to re-enable submission_attempts triggers", e);
        }
    }

    private void cleanupBusinessData() {
        jdbcTemplate.update(
                "DELETE FROM zatca_standard_lines WHERE "
                        + "header_id IN (SELECT id FROM "
                        + "zatca_standard_headers WHERE "
                        + "company_id = ?)", companyId);
        jdbcTemplate.update(
                "DELETE FROM zatca_standard_headers WHERE "
                        + "company_id = ?", companyId);
        jdbcTemplate.update(
                "DELETE FROM zatca_simplified_lines WHERE "
                        + "header_id IN (SELECT id FROM "
                        + "zatca_simplified_headers WHERE "
                        + "company_id = ?)", companyId);
        jdbcTemplate.update(
                "DELETE FROM zatca_simplified_headers WHERE "
                        + "company_id = ?", companyId);
        jdbcTemplate.update(
                "DELETE FROM user_company_transaction_roles "
                        + "WHERE company_id = ?", companyId);
        jdbcTemplate.update(
                "DELETE FROM zatca_chain_state WHERE "
                        + "company_id = ?", companyId);
        jdbcTemplate.update(
                "DELETE FROM users WHERE email = "
                        + "'zatca-audit@test.com'");
        jdbcTemplate.update(
                "DELETE FROM companies WHERE id = ?",
                companyId);
    }

    private int countAuditByType(String action, String entityType,
            String entityId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs "
                        + "WHERE action = ? AND entity_type = ? "
                        + "AND entity_id = ?",
                Integer.class, action, entityType, entityId);
    }

    private UUID auditUserId(String action, String entityId) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM audit_logs "
                        + "WHERE action = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                UUID.class, action, entityId);
    }

    private Short auditEnvId(String action, String entityId) {
        return jdbcTemplate.queryForObject(
                "SELECT authority_environment_id FROM audit_logs "
                        + "WHERE action = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                Short.class, action, entityId);
    }

    private JsonNode auditPayloadBefore(String action, String entityId)
            throws Exception {
        String json = jdbcTemplate.queryForObject(
                "SELECT payload_before FROM audit_logs "
                        + "WHERE action = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                String.class, action, entityId);
        return json != null ? objectMapper.readTree(json) : null;
    }

    private JsonNode auditPayloadAfter(String action, String entityId)
            throws Exception {
        String json = jdbcTemplate.queryForObject(
                "SELECT payload_after FROM audit_logs "
                        + "WHERE action = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                String.class, action, entityId);
        return json != null ? objectMapper.readTree(json) : null;
    }

    private void assertAuditCreated(String action, String entityType,
            String entityId, int before) {
        int after = countAuditByType(action, entityType, entityId);
        assertEquals(before + 1, after,
                "Expected one new audit entry for action=" + action
                        + " entityType=" + entityType);
        assertEquals(userId, auditUserId(action, entityId),
                "Audit entry should carry the actor's userId");
        assertEquals((short) 5, auditEnvId(action, entityId),
                "Audit entry should carry the authority_environment_id");
    }

    private String createStandardDraft(String number) throws Exception {
        var line = Map.of(
                "lineNumber", 1,
                "description", "Audit test service",
                "quantity", "1.00000",
                "unitPrice", "100.00000",
                "vatCategoryCode", "S",
                "vatRate", "15.00");

        String body = objectMapper.writeValueAsString(Map.of(
                "invoiceNumber", number,
                "invoiceTypeCode", "388",
                "transactionTypeCode", "0100000",
                "issueDate", "2026-05-20",
                "issueTime", "10:00:00",
                "currency", "SAR",
                "sellerData", Map.of(
                        "taxRegistrationNumber", "300000000000003",
                        "partyName", "Seller Co"),
                "buyerData", Map.of(
                        "taxRegistrationNumber", "300000000100003",
                        "partyName", "Buyer Co"),
                "lines", List.of(line)));

        String resp = mockMvc.perform(
                post("/api/companies/{companyId}/zatca/standard",
                        companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asText();
    }

    private String createSimplifiedDraft(String number) throws Exception {
        var line = Map.of(
                "lineNumber", 1,
                "description", "Audit test retail item",
                "quantity", "2.00000",
                "unitPrice", "50.00000",
                "vatCategoryCode", "S",
                "vatRate", "15.00");

        String body = objectMapper.writeValueAsString(Map.of(
                "invoiceNumber", number,
                "invoiceTypeCode", "388",
                "transactionTypeCode", "0200000",
                "issueDate", "2026-05-20",
                "issueTime", "14:00:00",
                "currency", "SAR",
                "sellerData", Map.of(
                        "taxRegistrationNumber", "300000000000003",
                        "partyName", "Seller Co"),
                "lines", List.of(line)));

        String resp = mockMvc.perform(
                post("/api/companies/{companyId}/zatca/simplified",
                        companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asText();
    }

    private void mockStandardSubmitSuccess() {
        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine
                        .ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hash123", "base64qr",
                        new byte[0]));
        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "CLEARED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));
    }

    private void mockSimplifiedSubmitSuccess() {
        when(engine.prepareSimplifiedSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine
                        .ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hashSimp", "base64qrSimp",
                        new byte[0]));
        when(engine.submitReporting(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "REPORTED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));
    }

    private void mockStandardSubmitAmbiguous() {
        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine
                        .ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hashAmb", "base64qrAmb",
                        new byte[0]));
        when(engine.submitClearance(any(), any(), any()))
                .thenThrow(new ResourceAccessException(
                        "Connection timeout"));
    }

    private void mockSimplifiedSubmitAmbiguous() {
        when(engine.prepareSimplifiedSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine
                        .ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hashAmbS", "base64qrAmbS",
                        new byte[0]));
        when(engine.submitReporting(any(), any(), any()))
                .thenThrow(new ResourceAccessException(
                        "Connection timeout"));
    }

    private void mockStandardSubmitRejected() {
        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine
                        .ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hashRej", "base64qrRej",
                        new byte[0]));
        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        false, "NOT_CLEARED", null, null,
                        null, 400, "Invalid document",
                        "{\"error\":\"rejected\"}"));
    }

    private void mockSimplifiedSubmitRejected() {
        when(engine.prepareSimplifiedSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine
                        .ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hashRejS", "base64qrRejS",
                        new byte[0]));
        when(engine.submitReporting(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        false, "NOT_REPORTED", null, null,
                        null, 400, "Invalid document",
                        "{\"error\":\"rejected\"}"));
    }

    private void mockCancelSuccess() {
        when(engine.cancel(any())).thenReturn(
                new AuthorityResponse(true, "SUCCESS",
                        null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));
    }

    private void mockCheckStatusCleared() {
        when(engine.checkStatus(any())).thenReturn(
                new AuthorityResponse(true, "CLEARED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));
    }

    private void mockCheckStatusReported() {
        when(engine.checkStatus(any())).thenReturn(
                new AuthorityResponse(true, "REPORTED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));
    }

    private void submitStandardSuccessfully(String docId)
            throws Exception {
        mockStandardSubmitSuccess();
        mockMvc.perform(
                post("/api/companies/{companyId}/zatca/standard"
                                + "/{docId}/submit",
                        companyId, docId)
                        .header("Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void submitSimplifiedSuccessfully(String docId)
            throws Exception {
        mockSimplifiedSubmitSuccess();
        mockMvc.perform(
                post("/api/companies/{companyId}/zatca/simplified"
                                + "/{docId}/submit",
                        companyId, docId)
                        .header("Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Nested
    class StandardActions {

        @Test
        void create_emitsCreateAudit() throws Exception {
            String docId = createStandardDraft("STD-AUD-C01");
            assertEquals(1,
                    countAuditByType("CREATE", "ZATCA_STANDARD",
                            docId),
                    "Expected exactly one CREATE audit row");
            assertEquals(userId,
                    auditUserId("CREATE", docId),
                    "Audit entry should carry the actor's userId");
            assertEquals((short) 5,
                    auditEnvId("CREATE", docId),
                    "Audit entry should carry the env id");

            JsonNode payloadAfter = auditPayloadAfter(
                    "CREATE", docId);
            assertNotNull(payloadAfter,
                    "CREATE should have payload_after");
            assertTrue(payloadAfter.has("invoiceNumber"),
                    "payload_after should contain invoiceNumber");
            assertEquals("STD-AUD-C01",
                    payloadAfter.get("invoiceNumber").asText());
        }

        @Test
        void edit_emitsEditAudit() throws Exception {
            String docId = createStandardDraft("STD-AUD-E01");
            int before = countAuditByType("EDIT", "ZATCA_STANDARD",
                    docId);

            var line = Map.of(
                    "lineNumber", 1,
                    "description", "Updated service",
                    "quantity", "1.00000",
                    "unitPrice", "100.00000",
                    "vatCategoryCode", "S",
                    "vatRate", "15.00");

            String editBody = objectMapper.writeValueAsString(
                    Map.of(
                            "invoiceNumber", "STD-AUD-E01",
                            "invoiceTypeCode", "388",
                            "transactionTypeCode", "0100000",
                            "issueDate", "2026-05-20",
                            "issueTime", "10:00:00",
                            "currency", "SAR",
                            "sellerData", Map.of(
                                    "taxRegistrationNumber",
                                    "300000000000003",
                                    "partyName",
                                    "Updated Seller"),
                            "buyerData", Map.of(
                                    "taxRegistrationNumber",
                                    "300000000100003",
                                    "partyName", "Buyer Co"),
                            "lines", List.of(line)));

            mockMvc.perform(
                    put("/api/companies/{companyId}"
                                    + "/zatca/standard/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody))
                    .andExpect(status().isOk());

            assertAuditCreated("EDIT", "ZATCA_STANDARD", docId,
                    before);

            JsonNode payloadAfter = auditPayloadAfter(
                    "EDIT", docId);
            assertNotNull(payloadAfter,
                    "EDIT should have payload_after");
            assertTrue(payloadAfter.has("version"),
                    "payload_after should contain version");
        }

        @Test
        void delete_emitsDeleteAudit() throws Exception {
            String docId = createStandardDraft("STD-AUD-D01");
            int before = countAuditByType("DELETE",
                    "ZATCA_STANDARD", docId);

            mockMvc.perform(
                    delete("/api/companies/{companyId}"
                                    + "/zatca/standard/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isNoContent());

            assertAuditCreated("DELETE", "ZATCA_STANDARD", docId,
                    before);
        }

        @Test
        void submit_emitsSubmitStandardAudit() throws Exception {
            String docId = createStandardDraft("STD-AUD-S01");
            int before = countAuditByType("SUBMIT_STANDARD",
                    "ZATCA_STANDARD", docId);

            submitStandardSuccessfully(docId);

            assertAuditCreated("SUBMIT_STANDARD", "ZATCA_STANDARD",
                    docId, before);
        }

        @Test
        void submitAmbiguous_emitsAmbiguousAudit() throws Exception {
            String docId = createStandardDraft("STD-AUD-SA01");
            int before = countAuditByType(
                    "SUBMIT_STANDARD_AMBIGUOUS",
                    "ZATCA_STANDARD", docId);

            mockStandardSubmitAmbiguous();
            mockMvc.perform(
                    post("/api/companies/{companyId}/zatca/standard"
                                    + "/{docId}/submit",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            assertAuditCreated("SUBMIT_STANDARD_AMBIGUOUS",
                    "ZATCA_STANDARD", docId, before);
        }

        @Test
        void cancel_emitsCancelStandardAudit() throws Exception {
            String docId = createStandardDraft("STD-AUD-CA01");
            submitStandardSuccessfully(docId);
            int before = countAuditByType("CANCEL_STANDARD",
                    "ZATCA_STANDARD", docId);

            mockCancelSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}/zatca/standard"
                                    + "/{docId}/cancel",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"reason\":\"test cancel\"}"))
                    .andExpect(status().isOk());

            assertAuditCreated("CANCEL_STANDARD", "ZATCA_STANDARD",
                    docId, before);
        }

        @Test
        void retry_emitsRetryStandardAudit() throws Exception {
            String docId = createStandardDraft("STD-AUD-R01");
            mockStandardSubmitAmbiguous();
            mockMvc.perform(
                    post("/api/companies/{companyId}/zatca/standard"
                                    + "/{docId}/submit",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            int before = countAuditByType("RETRY_STANDARD",
                    "ZATCA_STANDARD", docId);

            mockStandardSubmitSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}/zatca/standard"
                                    + "/{docId}/retry",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            assertAuditCreated("RETRY_STANDARD", "ZATCA_STANDARD",
                    docId, before);
        }

        @Test
        void checkStatus_emitsCheckStatusStandardAudit()
                throws Exception {
            String docId = createStandardDraft("STD-AUD-CS01");
            submitStandardSuccessfully(docId);

            jdbcTemplate.update(
                    "UPDATE zatca_standard_headers "
                            + "SET status = 'IN_REVIEW' "
                            + "WHERE id = ?::uuid",
                    docId);

            int before = countAuditByType("CHECK_STATUS_STANDARD",
                    "ZATCA_STANDARD", docId);

            mockCheckStatusCleared();
            mockMvc.perform(
                    post("/api/companies/{companyId}/zatca/standard"
                                    + "/{docId}/check-status",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            assertAuditCreated("CHECK_STATUS_STANDARD",
                    "ZATCA_STANDARD", docId, before);
        }

        @Test
        void cloneAsDraft_emitsCloneToNewDraftAudit()
                throws Exception {
            String docId = createStandardDraft("STD-AUD-CL01");
            mockStandardSubmitRejected();
            mockMvc.perform(
                    post("/api/companies/{companyId}/zatca/standard"
                                    + "/{docId}/submit",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            String cloneDocId;
            mockMvc.perform(
                    post("/api/companies/{companyId}/zatca/standard"
                                    + "/{docId}/clone-as-draft",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newInvoiceNumber\":"
                                    + "\"STD-AUD-CL02\"}"))
                    .andExpect(status().isCreated());

            cloneDocId = jdbcTemplate.queryForObject(
                    "SELECT id::text FROM zatca_standard_headers "
                            + "WHERE invoice_number = 'STD-AUD-CL02' "
                            + "AND company_id = ?::uuid",
                    String.class, companyId);
            assertNotNull(cloneDocId);

            int sourceAuditCount = countAuditByType(
                    "CLONE_TO_NEW_DRAFT", "ZATCA_STANDARD", docId);
            assertEquals(0, sourceAuditCount,
                    "Clone audit should not be keyed by source "
                            + "document id");

            int cloneAuditCount = countAuditByType(
                    "CLONE_TO_NEW_DRAFT", "ZATCA_STANDARD",
                    cloneDocId);
            assertEquals(1, cloneAuditCount,
                    "Clone audit should be keyed by clone "
                            + "document id");

            JsonNode payloadBefore = auditPayloadBefore(
                    "CLONE_TO_NEW_DRAFT", cloneDocId);
            assertNotNull(payloadBefore,
                    "CLONE_TO_NEW_DRAFT should have payload_before");
            assertTrue(
                    payloadBefore.has("sourceId"),
                    "payload_before should contain sourceId");
            assertEquals(docId,
                    payloadBefore.get("sourceId").asText(),
                    "sourceId should match the source document");

            JsonNode payloadAfter = auditPayloadAfter(
                    "CLONE_TO_NEW_DRAFT", cloneDocId);
            assertNotNull(payloadAfter,
                    "CLONE_TO_NEW_DRAFT should have payload_after");
            assertTrue(
                    payloadAfter.has("invoiceNumber"),
                    "payload_after should contain invoiceNumber");
        }

        @Test
        void allStandardAuditRows_carryCorrectEntityTypeAndEnvId()
                throws Exception {
            String docId = createStandardDraft("STD-AUD-CTX01");
            submitStandardSuccessfully(docId);

            int envRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE entity_type = 'ZATCA_STANDARD' "
                            + "AND entity_id = ? "
                            + "AND authority_environment_id = 5",
                    Integer.class, docId);
            int totalRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE entity_type = 'ZATCA_STANDARD' "
                            + "AND entity_id = ?",
                    Integer.class, docId);
            assertEquals(totalRows, envRows,
                    "All ZATCA_STANDARD audit rows must carry"
                            + " authority_environment_id = 5");

            int companyRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE entity_type = 'ZATCA_STANDARD' "
                            + "AND entity_id = ? "
                            + "AND company_id = ?::uuid",
                    Integer.class, docId, companyId.toString());
            assertEquals(totalRows, companyRows,
                    "All ZATCA_STANDARD audit rows must carry"
                            + " the correct company_id");
        }
    }

    @Nested
    class SimplifiedActions {

        @Test
        void create_emitsCreateAudit() throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-C01");
            assertEquals(1,
                    countAuditByType("CREATE", "ZATCA_SIMPLIFIED",
                            docId),
                    "Expected exactly one CREATE audit row");
            assertEquals(userId,
                    auditUserId("CREATE", docId),
                    "Audit entry should carry the actor's userId");
            assertEquals((short) 5,
                    auditEnvId("CREATE", docId),
                    "Audit entry should carry the env id");

            JsonNode payloadAfter = auditPayloadAfter(
                    "CREATE", docId);
            assertNotNull(payloadAfter,
                    "CREATE should have payload_after");
            assertTrue(payloadAfter.has("invoiceNumber"),
                    "payload_after should contain invoiceNumber");
            assertEquals("SIMP-AUD-C01",
                    payloadAfter.get("invoiceNumber").asText());
        }

        @Test
        void edit_emitsEditAudit() throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-E01");
            int before = countAuditByType("EDIT",
                    "ZATCA_SIMPLIFIED", docId);

            var line = Map.of(
                    "lineNumber", 1,
                    "description", "Updated item",
                    "quantity", "2.00000",
                    "unitPrice", "50.00000",
                    "vatCategoryCode", "S",
                    "vatRate", "15.00");

            String editBody = objectMapper.writeValueAsString(
                    Map.of(
                            "invoiceNumber", "SIMP-AUD-E01",
                            "invoiceTypeCode", "388",
                            "transactionTypeCode", "0200000",
                            "issueDate", "2026-05-20",
                            "issueTime", "14:00:00",
                            "currency", "SAR",
                            "sellerData", Map.of(
                                    "taxRegistrationNumber",
                                    "300000000000003",
                                    "partyName",
                                    "Updated Seller"),
                            "lines", List.of(line)));

            mockMvc.perform(
                    put("/api/companies/{companyId}"
                                    + "/zatca/simplified/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .header("If-Match", "\"0\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editBody))
                    .andExpect(status().isOk());

            assertAuditCreated("EDIT", "ZATCA_SIMPLIFIED", docId,
                    before);
        }

        @Test
        void delete_emitsDeleteAudit() throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-D01");
            int before = countAuditByType("DELETE",
                    "ZATCA_SIMPLIFIED", docId);

            mockMvc.perform(
                    delete("/api/companies/{companyId}"
                                    + "/zatca/simplified/{docId}",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isNoContent());

            assertAuditCreated("DELETE", "ZATCA_SIMPLIFIED", docId,
                    before);
        }

        @Test
        void submit_emitsSubmitSimplifiedAudit() throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-S01");
            int before = countAuditByType("SUBMIT_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", docId);

            submitSimplifiedSuccessfully(docId);

            assertAuditCreated("SUBMIT_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", docId, before);
        }

        @Test
        void submitAmbiguous_emitsAmbiguousAudit() throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-SA01");
            int before = countAuditByType(
                    "SUBMIT_SIMPLIFIED_AMBIGUOUS",
                    "ZATCA_SIMPLIFIED", docId);

            mockSimplifiedSubmitAmbiguous();
            mockMvc.perform(
                    post("/api/companies/{companyId}"
                                    + "/zatca/simplified"
                                    + "/{docId}/submit",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            assertAuditCreated("SUBMIT_SIMPLIFIED_AMBIGUOUS",
                    "ZATCA_SIMPLIFIED", docId, before);
        }

        @Test
        void cancel_emitsCancelSimplifiedAudit() throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-CA01");
            submitSimplifiedSuccessfully(docId);
            int before = countAuditByType("CANCEL_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", docId);

            mockCancelSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}"
                                    + "/zatca/simplified"
                                    + "/{docId}/cancel",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"reason\":\"test cancel\"}"))
                    .andExpect(status().isOk());

            assertAuditCreated("CANCEL_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", docId, before);
        }

        @Test
        void retry_emitsRetrySimplifiedAudit() throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-R01");
            mockSimplifiedSubmitAmbiguous();
            mockMvc.perform(
                    post("/api/companies/{companyId}"
                                    + "/zatca/simplified"
                                    + "/{docId}/submit",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            int before = countAuditByType("RETRY_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", docId);

            mockSimplifiedSubmitSuccess();
            mockMvc.perform(
                    post("/api/companies/{companyId}"
                                    + "/zatca/simplified"
                                    + "/{docId}/retry",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            assertAuditCreated("RETRY_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", docId, before);
        }

        @Test
        void checkStatus_emitsCheckStatusSimplifiedAudit()
                throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-CS01");
            submitSimplifiedSuccessfully(docId);

            jdbcTemplate.update(
                    "UPDATE zatca_simplified_headers "
                            + "SET status = 'IN_REVIEW' "
                            + "WHERE id = ?::uuid",
                    docId);

            int before = countAuditByType(
                    "CHECK_STATUS_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", docId);

            mockCheckStatusReported();
            mockMvc.perform(
                    post("/api/companies/{companyId}"
                                    + "/zatca/simplified"
                                    + "/{docId}/check-status",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            assertAuditCreated("CHECK_STATUS_SIMPLIFIED",
                    "ZATCA_SIMPLIFIED", docId, before);
        }

        @Test
        void cloneAsDraft_emitsCloneToNewDraftAudit()
                throws Exception {
            String docId = createSimplifiedDraft("SIMP-AUD-CL01");
            mockSimplifiedSubmitRejected();
            mockMvc.perform(
                    post("/api/companies/{companyId}"
                                    + "/zatca/simplified"
                                    + "/{docId}/submit",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(
                    post("/api/companies/{companyId}"
                                    + "/zatca/simplified"
                                    + "/{docId}/clone-as-draft",
                            companyId, docId)
                            .header("Authorization",
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newInvoiceNumber\":"
                                    + "\"SIMP-AUD-CL02\"}"))
                    .andExpect(status().isCreated());

            String cloneDocId = jdbcTemplate.queryForObject(
                    "SELECT id::text FROM "
                            + "zatca_simplified_headers "
                            + "WHERE invoice_number = 'SIMP-AUD-CL02' "
                            + "AND company_id = ?::uuid",
                    String.class, companyId);
            assertNotNull(cloneDocId);

            int sourceAuditCount = countAuditByType(
                    "CLONE_TO_NEW_DRAFT", "ZATCA_SIMPLIFIED",
                    docId);
            assertEquals(0, sourceAuditCount,
                    "Clone audit should not be keyed by source "
                            + "document id");

            int cloneAuditCount = countAuditByType(
                    "CLONE_TO_NEW_DRAFT", "ZATCA_SIMPLIFIED",
                    cloneDocId);
            assertEquals(1, cloneAuditCount,
                    "Clone audit should be keyed by clone "
                            + "document id");

            JsonNode payloadBefore = auditPayloadBefore(
                    "CLONE_TO_NEW_DRAFT", cloneDocId);
            assertNotNull(payloadBefore,
                    "CLONE_TO_NEW_DRAFT should have "
                            + "payload_before");
            assertTrue(
                    payloadBefore.has("sourceId"),
                    "payload_before should contain sourceId");
            assertEquals(docId,
                    payloadBefore.get("sourceId").asText(),
                    "sourceId should match the source document");
        }

        @Test
        void allSimplifiedAuditRows_carryCorrectEntityTypeAndEnvId()
                throws Exception {
            String docId =
                    createSimplifiedDraft("SIMP-AUD-CTX01");
            submitSimplifiedSuccessfully(docId);

            int envRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE entity_type = "
                            + "'ZATCA_SIMPLIFIED' "
                            + "AND entity_id = ? "
                            + "AND authority_environment_id = 5",
                    Integer.class, docId);
            int totalRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE entity_type = "
                            + "'ZATCA_SIMPLIFIED' "
                            + "AND entity_id = ?",
                    Integer.class, docId);
            assertEquals(totalRows, envRows,
                    "All ZATCA_SIMPLIFIED audit rows must carry"
                            + " authority_environment_id = 5");

            int companyRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE entity_type = "
                            + "'ZATCA_SIMPLIFIED' "
                            + "AND entity_id = ? "
                            + "AND company_id = ?::uuid",
                    Integer.class, docId,
                    companyId.toString());
            assertEquals(totalRows, companyRows,
                    "All ZATCA_SIMPLIFIED audit rows must carry"
                            + " the correct company_id");
        }
    }
}
