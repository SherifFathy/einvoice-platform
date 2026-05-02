package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.repository.BranchRepository;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.CustomerRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LovContextIsolationIT {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserCompanyRoleRepository userCompanyRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;
    private User user;
    private Customer zatcaCustomer;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        company = new Company();
        company.setNameAr("شركة عزل السياق");
        company.setNameEn("Context Isolation Co");
        company.setVatNumber("610000000000006");
        company.setCrNumber("CR006");
        company = companyRepository.save(company);

        user = User.builder()
                .name("Context Tester")
                .email("context-tester@lovit.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isActive(true)
                .build();
        user = userRepository.save(user);

        TenantContext.setCurrentTenantId(company.getId());
        TenantContext.setLovContextId(1L);

        zatcaCustomer = Customer.builder()
                .nameEn("ZATCA Sandbox Customer")
                .nameAr("عميل زاتكا")
                .customerType(CustomerType.B2C)
                .countryCode("SA")
                .build();
        zatcaCustomer.setCompany(company);
        zatcaCustomer.setLovContextId(1L);
        zatcaCustomer = customerRepository.save(zatcaCustomer);

        jdbcTemplate.update(
                "INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by) "
                        + "VALUES (?, ?, 1, 'VIEW_CUSTOMER_LIST', ?)",
                user.getId(), company.getId(), user.getId());
        jdbcTemplate.update(
                "INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by) "
                        + "VALUES (?, ?, 4, 'VIEW_CUSTOMER_LIST', ?)",
                user.getId(), company.getId(), user.getId());

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM user_context_permissions WHERE user_id = ?", user.getId());
        customerRepository.deleteAll();
        branchRepository.deleteAll();
        userCompanyRoleRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void customerInZatcaContext_notVisibleWhenLoggedInAsEtaContext() throws Exception {
        String etaToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(),
                company.getId(), "ACCOUNTANT",
                List.of("ZATCA_SANDBOX", "ETA_PREPRODUCTION"),
                List.of(Map.of("id", company.getId(), "name", company.getNameEn())),
                "ETA_PREPRODUCTION",
                "ETA", "INVOICE", "PREPROD",
                4L,
                List.of("VIEW_CUSTOMER_LIST"),
                false);

        MvcResult result = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + etaToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("ZATCA Sandbox Customer");
        assertThat(body).contains("\"totalElements\":0");
    }

    @Test
    void customerInZatcaContext_visibleWhenLoggedInAsSameContext() throws Exception {
        String zatcaToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(),
                company.getId(), "ACCOUNTANT",
                List.of("ZATCA_SANDBOX"),
                List.of(Map.of("id", company.getId(), "name", company.getNameEn())),
                "ZATCA_SANDBOX",
                "ZATCA", "INVOICE", "SANDBOX",
                1L,
                List.of("VIEW_CUSTOMER_LIST"),
                false);

        MvcResult result = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + zatcaToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("ZATCA Sandbox Customer");
    }
}
