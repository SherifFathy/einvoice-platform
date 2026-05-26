package com.einvoice.api.zatca.chain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base class for Phase 7 chain-integrity integration tests.
 *
 * <p>Placed in {@code platform-api} (not {@code platform-zatca}) because
 * {@code @SpringBootTest} requires the application class that lives in
 * {@code platform-api}; all existing Wave-8 ITs follow this same pattern.
 * Moving these tests to {@code platform-zatca} would require either
 * duplicating the Spring Boot application class or creating a circular
 * Maven dependency.</p>
 *
 * <p>Uses a singleton Testcontainers instance so all three concrete test
 * classes share one PostgreSQL container and one Spring application context,
 * cutting CI startup cost from 3 container launches to 1.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractZatcaChainIT {

    private static final Logger log =
            LoggerFactory.getLogger(AbstractZatcaChainIT.class);

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("einvoice_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected CompanyRepository companyRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtTokenProvider jwtTokenProvider;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @MockitoBean protected ZatcaAuthorityEngine engine;

    protected String token;
    protected UUID companyId;

    protected abstract String testEmail();

    protected void seedChainContext(String nameEn, String nameAr,
            String taxNumber, String userName) {
        Company company = companyRepository.save(Company.builder()
                .nameEn(nameEn).nameAr(nameAr)
                .taxNumber(taxNumber).isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name(userName)
                .email(testEmail())
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
        if (companyId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "DELETE FROM submission_attempts WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM invoice_artifacts WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM audit_logs WHERE "
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
                    "DELETE FROM users WHERE email = ?",
                    testEmail());
            jdbcTemplate.update(
                    "DELETE FROM companies WHERE id = ?",
                    companyId);
        } catch (Exception e) {
            log.warn("tearDown cleanup failed for companyId={}",
                    companyId, e);
        }
    }

    protected String createDraft(String invoiceNumber) throws Exception {
        var line = Map.of(
                "lineNumber", 1,
                "description", "Service",
                "quantity", "1.00000",
                "unitPrice", "100.00000",
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

        String response = mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }
}
