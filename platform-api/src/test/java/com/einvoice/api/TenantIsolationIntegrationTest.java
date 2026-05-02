package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.auth.dto.LoginRequest;
import com.einvoice.api.auth.dto.LoginResponse;
import com.einvoice.api.customer.dto.CustomerRequest;
import com.einvoice.api.customer.dto.CustomerResponse;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.CustomerRepository;
import com.einvoice.core.repository.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class TenantIsolationIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Company companyA;
    private Company companyB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        companyA = new Company();
        companyA.setNameAr("شركة أ");
        companyA.setNameEn("Company A");
        companyA.setVatNumber("100000000000001");
        companyA = companyRepository.save(companyA);

        companyB = new Company();
        companyB.setNameAr("شركة ب");
        companyB.setNameEn("Company B");
        companyB.setVatNumber("200000000000002");
        companyB = companyRepository.save(companyB);

        tokenA = jwtTokenProvider.generateAccessToken(
                1L, "Test User A", "a@test.com", companyA.getId(), "ACCOUNTANT",
                java.util.List.of("ZATCA_SANDBOX"),
                java.util.List.of(java.util.Map.of("id", companyA.getId(), "name", companyA.getNameEn())),
                "ZATCA_SANDBOX", "ZATCA", "INVOICE", "SANDBOX", 1L,
                java.util.List.of("VIEW_CUSTOMER_LIST"), false);
        tokenB = jwtTokenProvider.generateAccessToken(
                2L, "Test User B", "b@test.com", companyB.getId(), "ACCOUNTANT",
                java.util.List.of("ZATCA_SANDBOX"),
                java.util.List.of(java.util.Map.of("id", companyB.getId(), "name", companyB.getNameEn())),
                "ZATCA_SANDBOX", "ZATCA", "INVOICE", "SANDBOX", 1L,
                java.util.List.of("VIEW_CUSTOMER_LIST"), false);

        TenantContext.setCurrentTenantId(companyA.getId());
        Customer customerA = new Customer();
        customerA.setCompany(companyA);
        customerA.setNameEn("Customer A-1");
        customerA.setCustomerType(CustomerType.B2C);
        customerA.setCountryCode("SA");
        customerRepository.save(customerA);

        TenantContext.setCurrentTenantId(companyB.getId());
        Customer customerB = new Customer();
        customerB.setCompany(companyB);
        customerB.setNameEn("Customer B-1");
        customerB.setCustomerType(CustomerType.B2C);
        customerB.setCountryCode("SA");
        customerRepository.save(customerB);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        customerRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void companyACannotListCompanyBCustomers() throws Exception {
        MvcResult resultA = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult resultB = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();

        String bodyA = resultA.getResponse().getContentAsString();
        String bodyB = resultB.getResponse().getContentAsString();

        assertThat(bodyA).contains("Customer A-1");
        assertThat(bodyA).doesNotContain("Customer B-1");

        assertThat(bodyB).contains("Customer B-1");
        assertThat(bodyB).doesNotContain("Customer A-1");
    }

    @Test
    void companyACannotGetCompanyBCustomerById() throws Exception {
        TenantContext.setCurrentTenantId(companyB.getId());
        Customer customerB = customerRepository
                .findByCompanyIdAndIsActiveTrueOrderByNameEnAsc(companyB.getId())
                .get(0);
        TenantContext.clear();

        mockMvc.perform(get("/api/customers/{id}", customerB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void companyACannotUpdateCompanyBCustomer() throws Exception {
        TenantContext.setCurrentTenantId(companyB.getId());
        Long customerBId = customerRepository
                .findByCompanyIdAndIsActiveTrueOrderByNameEnAsc(companyB.getId())
                .get(0).getId();
        TenantContext.clear();

        CustomerRequest updateRequest = new CustomerRequest(
                null, "Hacked Name", null, null, null, null, null,
                null, null, null, "SA", "B2C", null, null);

        mockMvc.perform(patch("/api/customers/{id}", customerBId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void companyACannotDeleteCompanyBCustomer() throws Exception {
        TenantContext.setCurrentTenantId(companyB.getId());
        Long customerBId = customerRepository
                .findByCompanyIdAndIsActiveTrueOrderByNameEnAsc(companyB.getId())
                .get(0).getId();
        TenantContext.clear();

        mockMvc.perform(delete("/api/customers/{id}", customerBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        TenantContext.setCurrentTenantId(companyB.getId());
        assertThat(customerRepository.findById(customerBId))
                .isPresent();
        TenantContext.clear();
    }

    @Test
    void customerCreatedInCompanyADoesNotAppearInCompanyBList() throws Exception {
        CustomerRequest createRequest = new CustomerRequest(
                "اسم جديد", "New Customer A-2", null, null, null, null, null,
                null, null, null, "SA", "B2C", null, null);

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        MvcResult resultB = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();

        String bodyB = resultB.getResponse().getContentAsString();
        assertThat(bodyB).doesNotContain("New Customer A-2");
    }
}
