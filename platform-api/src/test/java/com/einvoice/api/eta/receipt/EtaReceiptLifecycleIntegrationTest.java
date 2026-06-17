package com.einvoice.api.eta.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.error.NoCertificateConfiguredException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.eta.engine.EtaAuthorityEngine;
import com.einvoice.security.jwt.JwtTokenProvider;
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
class EtaReceiptLifecycleIntegrationTest {

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

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Receipt Co").nameAr("شركة")
                .taxNumber("RC001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Receipt Admin")
                .email("admin@receipt-test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", (short) 2, companyId,
                TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        // Test-only: temporarily disable triggers to delete from
        // append-only tables.  Do NOT copy this pattern into
        // production code.
        jdbcTemplate.execute(
                "ALTER TABLE invoice_artifacts DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
        jdbcTemplate.update(
                "DELETE FROM invoice_artifacts "
                        + "WHERE company_id = ?", companyId);
        jdbcTemplate.update(
                "DELETE FROM submission_attempts "
                        + "WHERE company_id = ?", companyId);
        jdbcTemplate.update(
                "DELETE FROM audit_logs "
                        + "WHERE company_id = ?", companyId);
        jdbcTemplate.execute(
                "ALTER TABLE invoice_artifacts ENABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
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

    @Test
    void happyPath_createEditSubmit_dbRowsPresent() throws Exception {
        String createBody = """
                {"receiptNumber":"REC-001","documentType":"r",
                 "issueDatetime":"2026-05-13T14:30:00+02:00",
                 "sellerData":{"type":"B","name":"Seller"},
                 "currency":"EGP",
                 "totalSalesAmount":50,"totalCommercialDiscount":0,
                 "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                 "netAmount":50,"totalAmount":57,
                 "lines":[{
                   "lineNumber":1,"itemType":"EGS","itemCode":"EGS-1",
                   "description":"Test Item","unitType":"EA","quantity":2,
                   "unitPrice":25,
                   "salesTotal":50,
                   "valueDifference":0,"totalTaxableFees":0,"netTotal":50,
                   "taxAmount":7,"total":57,
                   "taxes":[{"taxType":"T1","taxRate":14,"taxAmount":7}]
                 }]}""";

        String createResponse = mockMvc.perform(
                post("/api/companies/{companyId}/eta/receipts",
                        companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(createResponse)
                .get("id").asText();

        String editBody = """
                {"receiptNumber":"REC-001","documentType":"r",
                 "issueDatetime":"2026-05-13T14:30:00+02:00",
                 "sellerData":{"type":"B","name":"Updated Seller"},
                 "currency":"EGP",
                 "totalSalesAmount":50,"totalCommercialDiscount":0,
                 "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                 "netAmount":50,"totalAmount":57,
                 "lines":[{
                   "lineNumber":1,"itemType":"EGS","itemCode":"EGS-1",
                   "description":"Test Item","unitType":"EA","quantity":2,
                   "unitPrice":25,
                   "salesTotal":50,
                   "valueDifference":0,"totalTaxableFees":0,"netTotal":50,
                   "taxAmount":7,"total":57,
                   "taxes":[{"taxType":"T1","taxRate":14,"taxAmount":7}]
                 }]}""";

        mockMvc.perform(put(
                "/api/companies/{companyId}/eta/receipts/{docId}",
                companyId, docId)
                        .header("Authorization", "Bearer " + token)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody))
                .andExpect(status().isOk());

        SignedPayload signedPayload = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep =
                new EtaAuthorityEngine.Preparation(
                        signedPayload, "clientId", "clientSecret",
                        "https://token.url");
        when(engine.prepareReceiptSubmission(any(), any(),
                anyShort())).thenReturn(prep);
        when(engine.httpSubmitReceipt(any(), any(), anyShort(), any()))
                .thenReturn(new AuthorityResponse(true, "SUCCESS",
                        "eta-rcpt-uuid-001", null, "sub-rcpt-001",
                        200, null,
                        "{\"uuid\":\"eta-rcpt-uuid-001\"}"));

        mockMvc.perform(post(
                "/api/companies/{companyId}/eta/receipts/{docId}/submit",
                companyId, docId)
                        .header("Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"));

        int artifactCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice_artifacts "
                        + "WHERE document_id = ?::uuid",
                Integer.class, docId);
        assertEquals(2, artifactCount);

        int attemptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM submission_attempts "
                        + "WHERE document_id = ?::uuid "
                        + "AND result = 'SUCCESS'",
                Integer.class, docId);
        assertEquals(1, attemptCount);

        int auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs "
                        + "WHERE entity_id = ?",
                Integer.class, docId);
        assertEquals(3, auditCount);

        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM eta_receipt_headers "
                        + "WHERE id = ?::uuid",
                String.class, docId);
        assertEquals("ACCEPTED", state);
    }
}
