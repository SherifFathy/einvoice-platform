package com.einvoice.api.eta.isolation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminModeRejectsOperationalEndpointsTest {

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
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID superUserId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Admin Rejection Co").nameAr("شركة").taxNumber("AR001").isActive(true).build());
        companyId = company.getId();

        User superUser = User.builder()
                .name("Admin Mode Test SU")
                .email("admin-mode-wave7@test.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        superUserId = superUser.getId();

        adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(superUserId);
        companyRepository.deleteById(companyId);
    }

    private void assertRejectsWithCompanyContextRequired(String method, String url) throws Exception {
        switch (method.toUpperCase()) {
          case "GET" -> mockMvc.perform(get(url, companyId)
                          .header("Authorization", "Bearer " + adminToken))
                  .andExpect(status().isForbidden())
                  .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
          case "POST" -> mockMvc.perform(post(url, companyId)
                          .header("Authorization", "Bearer " + adminToken)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{}"))
                  .andExpect(status().isForbidden())
                  .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
          case "PUT" -> mockMvc.perform(put(url, companyId, UUID.randomUUID())
                          .header("Authorization", "Bearer " + adminToken)
                          .header("If-Match", "\"0\"")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{}"))
                  .andExpect(status().isForbidden())
                  .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
          case "DELETE" -> mockMvc.perform(delete(url, companyId, UUID.randomUUID())
                          .header("Authorization", "Bearer " + adminToken))
                  .andExpect(status().isForbidden())
                  .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
          default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
    }

    @Test
    void adminMode_invoiceList_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("GET",
                "/api/companies/{companyId}/eta/invoices");
    }

    @Test
    void adminMode_invoiceCreate_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("POST",
                "/api/companies/{companyId}/eta/invoices");
    }

    @Test
    void adminMode_invoiceGet_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("GET",
                "/api/companies/{companyId}/eta/invoices/" + UUID.randomUUID());
    }

    @Test
    void adminMode_invoiceUpdate_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("PUT",
                "/api/companies/{companyId}/eta/invoices/{docId}");
    }

    @Test
    void adminMode_invoiceDelete_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("DELETE",
                "/api/companies/{companyId}/eta/invoices/{docId}");
    }

    @Test
    void adminMode_invoiceSubmit_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/submit",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_invoiceCancel_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/cancel",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_invoiceRetry_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/retry",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_invoiceBulkCheckStatus_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/check-status",
                        companyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_invoiceCloneAsDraft_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/invoices/{docId}/clone-as-draft",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceNumber\":\"NEW-001\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_invoiceSubmissions_rejected() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}/submissions",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_invoiceArtifactDownload_rejected() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/invoices/{docId}/artifacts/SIGNED_JSON",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_receiptList_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("GET",
                "/api/companies/{companyId}/eta/receipts");
    }

    @Test
    void adminMode_receiptCreate_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("POST",
                "/api/companies/{companyId}/eta/receipts");
    }

    @Test
    void adminMode_receiptGet_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("GET",
                "/api/companies/{companyId}/eta/receipts/" + UUID.randomUUID());
    }

    @Test
    void adminMode_receiptUpdate_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("PUT",
                "/api/companies/{companyId}/eta/receipts/{docId}");
    }

    @Test
    void adminMode_receiptDelete_rejected() throws Exception {
        assertRejectsWithCompanyContextRequired("DELETE",
                "/api/companies/{companyId}/eta/receipts/{docId}");
    }

    @Test
    void adminMode_receiptSubmit_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/receipts/{docId}/submit",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_receiptCancel_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/receipts/{docId}/cancel",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_receiptRetry_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/receipts/{docId}/retry",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_receiptBulkCheckStatus_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/receipts/check-status",
                        companyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_receiptCloneAsDraft_rejected() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/receipts/{docId}/clone-as-draft",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiptNumber\":\"NEW-R001\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_receiptSubmissions_rejected() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/receipts/{docId}/submissions",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_receiptArtifactDownload_rejected() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/receipts/{docId}/artifacts/SIGNED_JSON",
                        companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }
}
