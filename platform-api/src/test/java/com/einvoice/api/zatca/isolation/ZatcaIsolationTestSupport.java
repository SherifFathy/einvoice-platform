package com.einvoice.api.zatca.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
abstract class ZatcaIsolationTestSupport {

    static final short ZATCA_SANDBOX_ENV = 5;
    static final short ZATCA_PRODUCTION_ENV = 3;

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

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected CompanyRepository companyRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtTokenProvider jwtTokenProvider;
    @Autowired protected JdbcTemplate jdbcTemplate;

    protected UUID companyId;
    protected UUID userId;
    private final List<UUID> extraUserIds = new ArrayList<>();

    protected void trackExtraUser(UUID id) {
        extraUserIds.add(id);
    }

    protected Company createCompany(String nameEn, String taxNumber) {
        return companyRepository.save(Company.builder()
                .nameEn(nameEn).nameAr("شركة")
                .taxNumber(taxNumber).isActive(true).build());
    }

    protected User createSuperUser(String email) {
        User user = User.builder()
                .name("Iso User")
                .email(email)
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    protected User createRegularUser(String email) {
        User user = User.builder()
                .name("Iso Regular User")
                .email(email)
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    protected void grantRole(UUID userId, UUID companyId,
            short envId, String transactionType, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO user_company_transaction_roles "
                        + "(user_id, company_id, authority_environment_id, "
                        + "transaction_type, role_code) "
                        + "VALUES (?, ?, ?, ?, ?)",
                userId, companyId, envId, transactionType, roleCode);
    }

    protected void seedChainState(UUID companyId, short envId) {
        jdbcTemplate.update(
                "INSERT INTO zatca_chain_state "
                        + "(company_id, authority_environment_id, "
                        + "invoice_counter) "
                        + "VALUES (?, ?, 0)",
                companyId, envId);
    }

    protected String buildToken(UUID userId, String email,
            boolean isSuperUser, String authority, String environment,
            short envId, UUID companyId) {
        return jwtTokenProvider.createToken(userId, email, isSuperUser,
                authority, environment, envId, companyId,
                TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        deleteInReverseOrder();
    }

    protected void deleteInReverseOrder() {
        for (UUID extraId : extraUserIds) {
            jdbcTemplate.update(
                    "DELETE FROM user_company_transaction_roles "
                            + "WHERE user_id = ?", extraId);
        }
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
        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?", userId);
        }
        for (UUID extraId : extraUserIds) {
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?", extraId);
        }
        extraUserIds.clear();
        if (companyId != null) {
            jdbcTemplate.update(
                    "DELETE FROM companies WHERE id = ?",
                    companyId);
        }
    }

    protected Map<String, Object> standardPayload(String invoiceNumber) {
        return Map.of(
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
                "lines", List.of(Map.of(
                        "lineNumber", 1,
                        "description", "Consulting service",
                        "quantity", "1.00000",
                        "unitPrice", "1000.00000",
                        "vatCategoryCode", "S",
                        "vatRate", "15.00")));
    }

    protected Map<String, Object> simplifiedPayload(String invoiceNumber) {
        return Map.of(
                "invoiceNumber", invoiceNumber,
                "invoiceTypeCode", "388",
                "transactionTypeCode", "0200000",
                "issueDate", "2026-05-19",
                "issueTime", "14:30:00",
                "currency", "SAR",
                "sellerData", Map.of(
                        "taxRegistrationNumber", "300000000000003",
                        "partyName", "Seller Co"),
                "lines", List.of(Map.of(
                        "lineNumber", 1,
                        "description", "Retail item",
                        "quantity", "2.00000",
                        "unitPrice", "75.00000",
                        "vatCategoryCode", "S",
                        "vatRate", "15.00")));
    }

    protected ResultActions createStandardDoc(String token,
            String invoiceNumber) throws Exception {
        return mockMvc.perform(
                post("/api/companies/{companyId}/zatca/standard",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        standardPayload(invoiceNumber))));
    }

    protected ResultActions createSimplifiedDoc(String token,
            String invoiceNumber) throws Exception {
        return mockMvc.perform(
                post("/api/companies/{companyId}/zatca/simplified",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        simplifiedPayload(invoiceNumber))));
    }

    protected void assertChainStateExists(UUID companyId, short envId,
            int expectedCounter) {
        Integer counter = jdbcTemplate.queryForObject(
                "SELECT invoice_counter FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = ?",
                Integer.class, companyId, envId);
        assertThat(counter).as("chain counter for env %d", envId)
                .isNotNull().isEqualTo(expectedCounter);
    }

    protected void assertChainStatePreviousHashIsNull(UUID companyId,
            short envId) {
        String hash = jdbcTemplate.queryForObject(
                "SELECT previous_invoice_hash FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = ?",
                String.class, companyId, envId);
        assertThat(hash).as("previous_invoice_hash for env %d", envId)
                .isNull();
    }
}
