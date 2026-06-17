package com.einvoice.api.zatca.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.StatusInput;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BulkCheckStatusIT {

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
    @MockitoBean private ZatcaAuthorityEngine engine;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Bulk Test Co").nameAr("اختبار")
                .taxNumber("BT001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Test Admin")
                .email("bulk-test@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .build();
        user = userRepository.save(user);

        jdbcTemplate.update(
                "INSERT INTO user_company_transaction_roles "
                        + "(user_id, company_id, authority_environment_id, "
                        + "transaction_type, role_name) "
                        + "VALUES (?, ?, 5, 'STANDARD', 'COMPANY_ADMIN')",
                user.getId(), companyId);

        jdbcTemplate.update(
                "INSERT INTO user_company_transaction_roles "
                        + "(user_id, company_id, authority_environment_id, "
                        + "transaction_type, role_name) "
                        + "VALUES (?, ?, 5, 'SIMPLIFIED', 'COMPANY_ADMIN')",
                user.getId(), companyId);

        jdbcTemplate.update(
                "INSERT INTO zatca_chain_state "
                        + "(company_id, authority_environment_id, "
                        + "invoice_counter, updated_at) "
                        + "VALUES (?, ?, 0, NOW())",
                companyId, (short) 5);

        token = jwtTokenProvider.createToken(user.getId(),
                user.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5,
                companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update(
                    "DELETE FROM submission_attempts WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM invoice_artifacts WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM zatca_standard_lines WHERE "
                            + "header_id IN (SELECT id FROM "
                            + "zatca_standard_headers WHERE "
                            + "company_id = ?)", companyId);
            jdbcTemplate.update(
                    "DELETE FROM zatca_standard_headers WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM user_company_transaction_roles "
                            + "WHERE company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM zatca_chain_state WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM users WHERE email = "
                            + "'bulk-test@test.com'");
            jdbcTemplate.update(
                    "DELETE FROM companies WHERE id = ?",
                    companyId);
        } catch (Exception ignored) {
        }
    }

    @Test
    void bulkCheckStatusReturnsNdjsonStreamWith250Docs()
            throws Exception {
        List<UUID> docIds = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            UUID docId = createDocument("BULK-" + i + "-" + System.nanoTime());
            docIds.add(docId);
        }

        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine.ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<Signed/>".getBytes(),
                        "hash" + UUID.randomUUID(),
                        "qr64",
                        new byte[0]));
        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "IN_REVIEW", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));

        for (UUID docId : docIds) {
            mockMvc.perform(
                            post("/api/companies/{companyId}"
                                            + "/zatca/standard"
                                            + "/{docId}/submit",
                                    companyId, docId)
                                    .header("Authorization",
                                            "Bearer " + token))
                    .andExpect(status().isOk());
        }

        when(engine.checkStatus(any(StatusInput.class)))
                .thenReturn(new AuthorityResponse(
                        true, "CLEARED", null, null,
                        null, 200, null, "{}"));

        MvcResult result = mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard/check-status",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("documentIds", docIds))))
                .andExpect(status().isOk())
                .andExpect(header().exists("Run-Id"))
                .andReturn();

        String runId = result.getResponse().getHeader("Run-Id");
        assertNotNull(runId, "Run-Id header must be present");

        String body = result.getResponse().getContentAsString();
        String[] lines = body.split("\n");
        assertEquals(250, lines.length,
                "Should have 250 NDJSON lines for 250 documents");

        int updated = 0;
        int unchanged = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            var json = objectMapper.readTree(line);
            assertTrue(json.has("documentId"),
                    "Each line must have documentId");
            assertTrue(json.has("outcome"),
                    "Each line must have outcome");
            String outcome = json.get("outcome").asText();
            if ("UPDATED".equals(outcome)) {
                updated++;
            } else if ("UNCHANGED".equals(outcome)) {
                unchanged++;
            }
        }
        assertTrue(updated + unchanged > 0,
                "At least some documents should have an outcome");
    }

    @Test
    void cancelRunMidStreamProducesCancelledNoOpOutcomes()
            throws Exception {
        List<UUID> docIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID docId = createDocument("CANCEL-" + i + "-"
                    + System.nanoTime());
            docIds.add(docId);
        }

        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine.ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<Signed/>".getBytes(),
                        "hash" + UUID.randomUUID(),
                        "qr64",
                        new byte[0]));
        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "IN_REVIEW", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));

        for (UUID docId : docIds) {
            mockMvc.perform(
                            post("/api/companies/{companyId}"
                                            + "/zatca/standard"
                                            + "/{docId}/submit",
                                    companyId, docId)
                                    .header("Authorization",
                                            "Bearer " + token))
                    .andExpect(status().isOk());
        }

        when(engine.checkStatus(any(StatusInput.class)))
                .thenAnswer(inv -> {
                    Thread.sleep(150);
                    return new AuthorityResponse(
                            true, "CLEARED", null, null,
                            null, 200, null, "{}");
                });

        MvcResult asyncResult = mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/check-status",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("documentIds", docIds))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String runId = asyncResult.getResponse().getHeader("Run-Id");
        assertNotNull(runId, "Run-Id header must be present before dispatch");

        mockMvc.perform(
                        delete("/api/runs/{runId}", runId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk());

        String body = asyncResult.getResponse().getContentAsString();
        assertNotNull(body, "Response body must not be null");
        assertFalse(body.isBlank(),
                "Response body must not be empty");

        String[] lines = body.split("\n");
        int cancelledCount = 0;
        int processedCount = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            var json = objectMapper.readTree(line);
            assertTrue(json.has("documentId"),
                    "Each line must have documentId");
            assertTrue(json.has("outcome"),
                    "Each line must have outcome");
            String outcome = json.get("outcome").asText();
            switch (outcome) {
              case "CANCELLED_NO_OP" -> cancelledCount++;
              case "UPDATED", "UNCHANGED" -> processedCount++;
              default -> fail("Unexpected outcome: " + outcome);
            }
        }
        assertTrue(cancelledCount >= 1,
                "At least one document should be CANCELLED_NO_OP, "
                        + "got " + cancelledCount);
        assertTrue(processedCount >= 1,
                "At least one document should have been processed "
                        + "before cancel, got " + processedCount);
        assertEquals(docIds.size(), cancelledCount + processedCount,
                "Total outcomes must equal document count");
    }

    @Test
    void cancelUnknownRunReturns404() throws Exception {
        mockMvc.perform(
                        delete("/api/runs/{runId}",
                                UUID.randomUUID().toString())
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRunByNonOwnerReturns404() throws Exception {
        Company otherCompany = companyRepository.save(Company.builder()
                .nameEn("Other Co").nameAr("أخرى")
                .taxNumber("OC001").isActive(true).build());
        User otherUser = User.builder()
                .name("Other Admin")
                .email("other-bulk@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .build();
        otherUser = userRepository.save(otherUser);

        jdbcTemplate.update(
                "INSERT INTO user_company_transaction_roles "
                        + "(user_id, company_id, authority_environment_id, "
                        + "transaction_type, role_name) "
                        + "VALUES (?, ?, 5, 'STANDARD', 'COMPANY_ADMIN')",
                otherUser.getId(), otherCompany.getId());

        final String otherToken = jwtTokenProvider.createToken(otherUser.getId(),
                otherUser.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5,
                otherCompany.getId(),
                TenantContext.Mode.OPERATIONAL_MODE);

        List<UUID> docIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID docId = createDocument("OWNER-" + i + "-"
                    + System.nanoTime());
            docIds.add(docId);
        }

        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine.ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<Signed/>".getBytes(),
                        "hash" + UUID.randomUUID(),
                        "qr64",
                        new byte[0]));
        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "IN_REVIEW", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));

        for (UUID docId : docIds) {
            mockMvc.perform(
                            post("/api/companies/{companyId}"
                                            + "/zatca/standard"
                                            + "/{docId}/submit",
                                    companyId, docId)
                                    .header("Authorization",
                                            "Bearer " + token))
                    .andExpect(status().isOk());
        }

        when(engine.checkStatus(any(StatusInput.class)))
                .thenReturn(new AuthorityResponse(
                        true, "CLEARED", null, null,
                        null, 200, null, "{}"));

        MvcResult result = mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard/check-status",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("documentIds", docIds))))
                .andExpect(status().isOk())
                .andReturn();

        String runId = result.getResponse().getHeader("Run-Id");
        assertNotNull(runId);

        mockMvc.perform(
                        delete("/api/runs/{runId}", runId)
                                .header("Authorization",
                                        "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        try {
            jdbcTemplate.update(
                    "DELETE FROM user_company_transaction_roles "
                            + "WHERE user_id = ? AND company_id = ?",
                    otherUser.getId(), otherCompany.getId());
            jdbcTemplate.update(
                    "DELETE FROM users WHERE email = "
                            + "'other-bulk@test.com'");
            jdbcTemplate.update(
                    "DELETE FROM companies WHERE id = ?",
                    otherCompany.getId());
        } catch (Exception ignored) {
        }
    }

    private UUID createDocument(String invoiceNumber) throws Exception {
        var line = Map.of(
                "lineNumber", 1,
                "description", "Service",
                "quantity", "100.00000",
                "unitPrice", "10.00000",
                "vatCategoryCode", "S",
                "vatRate", "15.00");

        String body = objectMapper.writeValueAsString(Map.of(
                "invoiceNumber", invoiceNumber,
                "invoiceTypeCode", "388",
                "transactionTypeCode", "0100000",
                "issueDate", "2026-05-19",
                "issueTime", "14:30:00",
                "currency", "SAR",
                "sellerData", Map.of(
                        "taxRegistrationNumber", "300000000000003",
                        "partyName", "Seller Co"),
                "buyerData", Map.of(
                        "taxRegistrationNumber", "300000000100003",
                        "partyName", "Buyer Co"),
                "lines", List.of(line)));

        String createResponse = mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(createResponse)
                .get("id").asText());
    }
}
