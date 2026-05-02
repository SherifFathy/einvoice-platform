package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.CustomerRepository;
import com.einvoice.core.service.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class CustomerServiceIT {

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
    private CustomerService customerService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        company = new Company();
        company.setNameAr("شركة اختبار العملاء");
        company.setNameEn("Customer Test Company");
        company.setVatNumber("550000000000005");
        company.setCrNumber("CR005");
        company = companyRepository.save(company);

        Long superUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE is_super_user = true LIMIT 1",
                Long.class);
        if (superUserId == null) {
            superUserId = jdbcTemplate.queryForObject(
                    "INSERT INTO users (name, email, password_hash, is_active, "
                            + "is_super_user) VALUES ('Super','super-cust@test.com',"
                            + "'x',true,true) RETURNING id",
                    Long.class);
        }

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(superUserId, "credentials"));

        TenantContext.setCurrentTenantId(company.getId());
        TenantContext.setLovContextId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        customerRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void testCreateCustomer_success() {
        Customer customer = Customer.builder()
                .nameEn("Test Customer")
                .nameAr("عميل اختبار")
                .customerType(CustomerType.B2B)
                .vatNumber("300000000000003")
                .countryCode("SA")
                .build();

        Customer saved = customerService.create(customer);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNameEn()).isEqualTo("Test Customer");
        assertThat(saved.getLovContextId()).isEqualTo(1L);
        assertThat(saved.getCompany().getId()).isEqualTo(company.getId());
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    void testCreateCustomer_lovContextIdSetFromTenantContext() {
        TenantContext.setLovContextId(2L);

        Customer customer = Customer.builder()
                .nameEn("Context Customer")
                .customerType(CustomerType.B2C)
                .countryCode("SA")
                .build();

        Customer saved = customerService.create(customer);

        assertThat(saved.getLovContextId()).isEqualTo(2L);
    }

    @Test
    void testCreateCustomer_noLovContext_throws() {
        TenantContext.setLovContextId(null);

        Customer customer = Customer.builder()
                .nameEn("No Context Customer")
                .customerType(CustomerType.B2C)
                .countryCode("SA")
                .build();

        assertThatThrownBy(() -> customerService.create(customer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active LOV context");
    }

    @Test
    void testCreateCustomer_b2bWithoutVat_throws() {
        Customer customer = Customer.builder()
                .nameEn("B2B No VAT")
                .customerType(CustomerType.B2B)
                .countryCode("SA")
                .build();

        assertThatThrownBy(() -> customerService.create(customer))
                .isInstanceOf(CustomerService.B2bVatRequiredException.class);
    }
}
