package com.einvoice.api.eta.invoice;

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
class EtaInvoiceLifecycleIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
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
                .nameEn("Lifecycle Co").nameAr("شركة").taxNumber("LC001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Lifecycle Admin")
                .email("admin@lifecycle-test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", (short) 2, companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("ALTER TABLE invoice_artifacts DISABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
        jdbcTemplate.update("DELETE FROM invoice_artifacts WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM submission_attempts WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE company_id = ?", companyId);
        jdbcTemplate.execute("ALTER TABLE invoice_artifacts ENABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
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
        jdbcTemplate.update("DELETE FROM eta_invoice_headers WHERE company_id = ?", companyId);
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void scenarioA_happyPath_createEditSubmit_dbRowsPresent() throws Exception {
        String createBody = """
                {"invoiceNumber":"INV-001","documentType":"i",
                 "issueDatetime":"2026-05-13T10:00:00+02:00",
                 "sellerData":{"type":"B","name":"Seller"},
                 "buyerData":{"type":"P","name":"Buyer"},
                 "currency":"EGP",
                 "totalSalesAmount":100,"totalDiscountAmount":0,
                 "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                 "netAmount":100,"totalAmount":100,
                 "lines":[{
                   "lineNumber":1,"itemType":"GS1","itemCode":"ITM-1",
                   "description":"Test Item","unitType":"EA","quantity":1,
                   "unitValue":{"currencySold":"EGP","amountEGP":100,"amountSold":100,"currencyExchangeRate":1},
                   "salesTotal":100,"discountAmount":0,"itemsDiscount":0,
                   "valueDifference":0,"totalTaxableFees":0,"netTotal":100,
                   "taxAmount":0,"total":100,
                   "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
                 }]}""";

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/invoices", companyId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(createResponse).get("id").asText();

        String editBody = """
                {"invoiceNumber":"INV-001","documentType":"i",
                 "issueDatetime":"2026-05-13T10:00:00+02:00",
                 "sellerData":{"type":"B","name":"Updated Seller"},
                 "buyerData":{"type":"P","name":"Buyer"},
                 "currency":"EGP",
                 "totalSalesAmount":100,"totalDiscountAmount":0,
                 "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                 "netAmount":100,"totalAmount":100,
                 "lines":[{
                   "lineNumber":1,"itemType":"GS1","itemCode":"ITM-1",
                   "description":"Test Item","unitType":"EA","quantity":1,
                   "unitValue":{"currencySold":"EGP","amountEGP":100,"amountSold":100,"currencyExchangeRate":1},
                   "salesTotal":100,"discountAmount":0,"itemsDiscount":0,
                   "valueDifference":0,"totalTaxableFees":0,"netTotal":100,
                   "taxAmount":0,"total":100,
                   "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
                 }]}""";

        mockMvc.perform(put("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId)
                .header("Authorization", "Bearer " + token)
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody))
            .andExpect(status().isOk());

        SignedPayload signedPayload = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep = new EtaAuthorityEngine.Preparation(
                signedPayload, "clientId", "clientSecret", "https://token.url");
        when(engine.prepareSubmission(any(), any(), anyShort())).thenReturn(prep);
        when(engine.httpSubmit(any(), any(), anyShort(), any()))
            .thenReturn(new AuthorityResponse(true, "SUCCESS",
                    "eta-uuid-001", "long-1", "sub-001",
                    200, null, "{\"uuid\":\"eta-uuid-001\"}"));

        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/submit", companyId, docId)
                .header("Authorization", "Bearer " + token))
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

        int createAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs "
                        + "WHERE entity_id = ? "
                        + "AND action = 'CREATE_INVOICE'",
                Integer.class, docId);
        assertEquals(1, createAuditCount);
        int editAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs "
                        + "WHERE entity_id = ? "
                        + "AND action = 'EDIT_INVOICE'",
                Integer.class, docId);
        assertEquals(1, editAuditCount);
        int submitAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs "
                        + "WHERE entity_id = ? "
                        + "AND action = 'SUBMIT_INVOICE'",
                Integer.class, docId);
        assertEquals(1, submitAuditCount);
    }

    @Test
    void scenarioB_noCertificate_txRollback_noRowsWritten() throws Exception {
        String createBody = """
                {"invoiceNumber":"INV-B01","documentType":"i",
                 "issueDatetime":"2026-05-13T10:00:00+02:00",
                 "sellerData":{"type":"B","name":"Seller"},
                 "buyerData":{"type":"P","name":"Buyer"},
                 "currency":"EGP",
                 "totalSalesAmount":100,"totalDiscountAmount":0,
                 "extraDiscountAmount":0,"totalItemsDiscountAmount":0,
                 "netAmount":100,"totalAmount":100,
                 "lines":[{
                   "lineNumber":1,"itemType":"GS1","itemCode":"ITM-1",
                   "description":"Test Item","unitType":"EA","quantity":1,
                   "unitValue":{"currencySold":"EGP","amountEGP":100,"amountSold":100,"currencyExchangeRate":1},
                   "salesTotal":100,"discountAmount":0,"itemsDiscount":0,
                   "valueDifference":0,"totalTaxableFees":0,"netTotal":100,
                   "taxAmount":0,"total":100,
                   "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
                 }]}""";

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/invoices", companyId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(createResponse).get("id").asText();

        when(engine.prepareSubmission(any(), any(), anyShort()))
            .thenThrow(new NoCertificateConfiguredException("No ETA config", companyId, (short) 2));

        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/submit", companyId, docId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NO_CERTIFICATE_CONFIGURED"));

        int artifactCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice_artifacts "
                        + "WHERE document_id = ?::uuid",
                Integer.class, docId);
        assertEquals(0, artifactCount);

        int attemptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM submission_attempts "
                        + "WHERE document_id = ?::uuid",
                Integer.class, docId);
        assertEquals(0, attemptCount);

        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM eta_invoice_headers "
                        + "WHERE id = ?::uuid",
                String.class, docId);
        assertEquals("DRAFT", state);
    }
}
