package com.einvoice.api.eta.isolation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EtaInvoiceCrossEnvIsolationTest {

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
    @Autowired private UserCompanyTransactionRoleRepository uctrRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EtaAuthorityEngine engine;

    private UUID companyId;
    private UUID superUserId;
    private UUID nonSuperUserId;
    private String superPreprodToken;
    private String superProdToken;
    private String nonSuperPreprodToken;
    private String nonSuperProdToken;

    private static final String INVOICE_BODY = """
            {"invoiceNumber":"INV-ISO-001","documentType":"i",
             "issueDatetime":"2026-05-14T10:00:00+02:00",
             "sellerData":{"type":"B","name":"Seller"},
             "buyerData":{"type":"P","name":"Buyer"},
             "currency":"EGP",
             "lines":[{
               "lineNumber":1,"itemType":"GS1","itemCode":"ITM-1",
               "description":"Isolation Test Item","unitType":"EA","quantity":1,
               "unitValue":{"currencySold":"EGP","amountEGP":100,"amountSold":100,"currencyExchangeRate":1},
               "salesTotal":100,"discountAmount":0,"itemsDiscount":0,
               "valueDifference":0,"totalTaxableFees":0,"netTotal":100,
               "taxAmount":0,"total":100,
               "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
             }]}""";

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Cross Env Iso Co").nameAr("شركة").taxNumber("CEI001").isActive(true).build());
        companyId = company.getId();

        User superUser = User.builder()
                .name("Cross Env Iso SU")
                .email("crossenv-inv-su@test.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        superUserId = superUser.getId();

        superPreprodToken = tokenFor(superUser, (short) 2, companyId);
        superProdToken = tokenFor(superUser, (short) 1, companyId);

        User nonSuperUser = User.builder()
                .name("Cross Env Iso NonSU")
                .email("crossenv-inv-nonsu@test.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        nonSuperUser = userRepository.save(nonSuperUser);
        nonSuperUserId = nonSuperUser.getId();

        uctrRepository.save(UserCompanyTransactionRole.builder()
                .user(nonSuperUser)
                .company(company)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE")
                .roleCode("COMPANY_ADMIN")
                .isActive(true)
                .build());

        nonSuperPreprodToken = tokenFor(nonSuperUser, (short) 2, companyId);
        nonSuperProdToken = tokenFor(nonSuperUser, (short) 1, companyId);
    }

    @AfterEach
    void tearDown() {
        disableTriggers();
        try {
            jdbcTemplate.update("DELETE FROM invoice_artifacts WHERE company_id = ?", companyId);
            jdbcTemplate.update("DELETE FROM submission_attempts WHERE company_id = ?", companyId);
            jdbcTemplate.update("DELETE FROM audit_logs WHERE company_id = ?", companyId);
        } finally {
            enableTriggers();
        }

        jdbcTemplate.update(
                "DELETE FROM eta_invoice_line_taxes "
                        + "WHERE line_id IN ("
                        + "SELECT l.id FROM eta_invoice_lines l "
                        + "JOIN eta_invoice_headers h ON l.header_id = h.id "
                        + "WHERE h.company_id = ?)", companyId);
        jdbcTemplate.update(
                "DELETE FROM eta_invoice_lines "
                        + "WHERE header_id IN ("
                        + "SELECT id FROM eta_invoice_headers "
                        + "WHERE company_id = ?)", companyId);
        jdbcTemplate.update("DELETE FROM eta_invoice_headers WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM user_company_transaction_roles WHERE user_id = ?", nonSuperUserId);
        userRepository.deleteById(superUserId);
        userRepository.deleteById(nonSuperUserId);
        companyRepository.deleteById(companyId);
    }

    private String tokenFor(User user, short envId, UUID compId) {
        return jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), user.getIsSuperUser(),
                "ETA",
                envId == 2 ? "PREPROD" : "PRODUCTION",
                envId, compId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    private MvcResult createDraft(String token) throws Exception {
        return mockMvc.perform(post("/api/companies/{companyId}/eta/invoices", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVOICE_BODY))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractDocId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void stubEngineSuccess() {
        SignedPayload signedPayload = new SignedPayload(
                "payload".getBytes(), "sig".getBytes(), "CAdES-BES");
        EtaAuthorityEngine.Preparation prep = new EtaAuthorityEngine.Preparation(
                signedPayload, "clientId", "clientSecret", "https://token.url");
        when(engine.prepareSubmission(any(), any(), anyShort())).thenReturn(prep);
        when(engine.httpSubmit(any(), any(), anyShort(), any()))
                .thenReturn(new AuthorityResponse(true, "SUCCESS",
                        "eta-uuid-001", "long-1", "sub-001",
                        200, null, "{\"uuid\":\"eta-uuid-001\"}"));
    }

    @Test
    void invoiceCreatedInPreprod_notVisibleInProd_list() throws Exception {
        createDraft(superPreprodToken);

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices", companyId)
                        .header("Authorization", "Bearer " + superProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices", companyId)
                        .header("Authorization", "Bearer " + superPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void invoiceCreatedInPreprod_getById_returns404_inProd() throws Exception {
        String docId = extractDocId(createDraft(superPreprodToken));

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId)
                        .header("Authorization", "Bearer " + superProdToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}", companyId, docId)
                        .header("Authorization", "Bearer " + superPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId));
    }

    @Test
    void invoiceCreatedInPreprod_bulkCheckStatus_nonSuperUser_forbiddenFromProd() throws Exception {
        String docId = extractDocId(createDraft(superPreprodToken));

        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/check-status", companyId)
                        .header("Authorization", "Bearer " + nonSuperProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[\"" + docId + "\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invoiceCreatedInPreprod_bulkCheckStatus_nonSuperUser_allowedFromPreprod() throws Exception {
        String docId = extractDocId(createDraft(superPreprodToken));

        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/check-status", companyId)
                        .header("Authorization", "Bearer " + nonSuperPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[\"" + docId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    void invoiceSubmittedInPreprod_submissionsEndpoint_crossEnv_returns200_not404() throws Exception {
        String docId = extractDocId(createDraft(superPreprodToken));

        stubEngineSuccess();

        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/submit", companyId, docId)
                        .header("Authorization", "Bearer " + superPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VALID"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}/submissions", companyId, docId)
                        .header("Authorization", "Bearer " + superProdToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}/submissions", companyId, docId)
                        .header("Authorization", "Bearer " + superPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invoiceSubmittedInPreprod_artifactEndpoint_crossEnv_returns404_inProd() throws Exception {
        String docId = extractDocId(createDraft(superPreprodToken));

        stubEngineSuccess();

        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/submit", companyId, docId)
                        .header("Authorization", "Bearer " + superPreprodToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}/artifacts/SIGNED_JSON", companyId, docId)
                        .header("Authorization", "Bearer " + superProdToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}/artifacts/SIGNED_JSON", companyId, docId)
                        .header("Authorization", "Bearer " + superPreprodToken))
                .andExpect(status().isOk());
    }

    @Test
    void invoicesIsolated_inBothDirections() throws Exception {
        String prodBody = """
                {"invoiceNumber":"INV-PROD-001","documentType":"i",
                 "issueDatetime":"2026-05-14T10:00:00+02:00",
                 "sellerData":{"type":"B","name":"Seller Prod"},
                 "buyerData":{"type":"P","name":"Buyer Prod"},
                 "currency":"EGP",
                 "lines":[{
                   "lineNumber":1,"itemType":"GS1","itemCode":"ITM-P",
                   "description":"Prod Item","unitType":"EA","quantity":1,
                   "unitValue":{"currencySold":"EGP","amountEGP":200,"amountSold":200,"currencyExchangeRate":1},
                   "salesTotal":200,"discountAmount":0,"itemsDiscount":0,
                   "valueDifference":0,"totalTaxableFees":0,"netTotal":200,
                   "taxAmount":0,"total":200,
                   "taxes":[{"taxType":"V1","taxRate":0,"taxAmount":0}]
                 }]}""";

        createDraft(superPreprodToken);

        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices", companyId)
                        .header("Authorization", "Bearer " + superProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prodBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices", companyId)
                        .header("Authorization", "Bearer " + superPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("INV-ISO-001"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices", companyId)
                        .header("Authorization", "Bearer " + superProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].invoiceNumber").value("INV-PROD-001"));
    }

    private void disableTriggers() {
        jdbcTemplate.execute("ALTER TABLE invoice_artifacts DISABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
    }

    private void enableTriggers() {
        jdbcTemplate.execute("ALTER TABLE invoice_artifacts ENABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        jdbcTemplate.execute("ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
    }
}
