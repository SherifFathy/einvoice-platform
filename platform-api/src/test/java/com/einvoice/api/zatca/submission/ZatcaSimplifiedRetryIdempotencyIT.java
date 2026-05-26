package com.einvoice.api.zatca.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ZatcaSimplifiedRetryIdempotencyIT {

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
                .nameEn("Simp Retry Co").nameAr("اختبار")
                .taxNumber("SR001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Test Admin")
                .email("simp-retry@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .build();
        user = userRepository.save(user);

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
                            + "'simp-retry@test.com'");
            jdbcTemplate.update(
                    "DELETE FROM companies WHERE id = ?",
                    companyId);
        } catch (Exception ignored) {
        }
    }

    @Test
    void retryAfterAmbiguousSimplifiedProducesNewAttemptNoChainAdvance()
            throws Exception {
        var line = Map.of(
                "lineNumber", 1,
                "description", "Retail service",
                "quantity", "1.00000",
                "unitPrice", "300.00000",
                "vatCategoryCode", "S",
                "vatRate", "15.00");

        String body = objectMapper.writeValueAsString(Map.of(
                "invoiceNumber", "SMP-RT-" + System.nanoTime(),
                "invoiceTypeCode", "388",
                "transactionTypeCode", "0200000",
                "issueDate", "2026-05-19",
                "issueTime", "14:30:00",
                "currency", "SAR",
                "sellerData", Map.of(
                        "taxRegistrationNumber", "300000000000003",
                        "partyName", "Seller Co"),
                "lines", List.of(line)));

        String createResponse = mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/simplified",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(createResponse)
                .get("id").asText();

        when(engine.prepareSimplifiedSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine
                        .ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "abc123",
                        "base64qr",
                        new byte[0]));

        when(engine.submitReporting(any(), any(), any()))
                .thenThrow(new RuntimeException("Gateway timeout"));

        mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/simplified"
                                        + "/{docId}/submit",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_REVIEW"));

        when(engine.submitReporting(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "REPORTED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));

        mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/simplified"
                                        + "/{docId}/retry",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"));

        mockMvc.perform(
                        get("/api/companies/{companyId}"
                                        + "/zatca/simplified"
                                        + "/{docId}/submissions",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        long counter = jdbcTemplate.queryForObject(
                "SELECT invoice_counter FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5",
                Long.class, companyId);
        assertEquals(1, counter,
                "Chain counter should be exactly 1 after "
                        + "initial submit (retry does NOT advance)");
    }
}
