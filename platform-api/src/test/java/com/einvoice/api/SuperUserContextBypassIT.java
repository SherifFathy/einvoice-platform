package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.CustomerRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserRepository;
import com.einvoice.core.service.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class SuperUserContextBypassIT {

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
    private UserRepository userRepository;

    @Autowired
    private UserCompanyRoleRepository userCompanyRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Company company;
    private User superUser;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        company = new Company();
        company.setNameAr("شركة المستخدم الخارق");
        company.setNameEn("Super User Test Co");
        company.setVatNumber("820000000000008");
        company.setCrNumber("CR008");
        company = companyRepository.save(company);

        superUser = User.builder()
                .name("Super User")
                .email("superuser-bypass@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isActive(true)
                .isSuperUser(true)
                .build();
        superUser = userRepository.save(superUser);

        TenantContext.setCurrentTenantId(company.getId());
        TenantContext.setLovContextId(1L);

        Customer zatcaCustomer = Customer.builder()
                .nameEn("ZATCA Customer")
                .customerType(CustomerType.B2C)
                .countryCode("SA")
                .build();
        zatcaCustomer.setCompany(company);
        zatcaCustomer.setLovContextId(1L);
        customerRepository.save(zatcaCustomer);

        Customer etaCustomer = Customer.builder()
                .nameEn("ETA Customer")
                .customerType(CustomerType.B2C)
                .countryCode("EG")
                .build();
        etaCustomer.setCompany(company);
        etaCustomer.setLovContextId(4L);
        customerRepository.save(etaCustomer);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        customerRepository.deleteAll();
        userCompanyRoleRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void superUser_seesCustomersAcrossAllContexts() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(superUser.getId(), "credentials"));
        TenantContext.setCurrentTenantId(company.getId());
        TenantContext.setLovContextId(1L);

        var customers = customerService.list(null, null, null, PageRequest.of(0, 50));

        assertThat(customers.getTotalElements()).isEqualTo(2);
        assertThat(customers.getContent())
                .anyMatch(c -> "ZATCA Customer".equals(c.getNameEn()));
        assertThat(customers.getContent())
                .anyMatch(c -> "ETA Customer".equals(c.getNameEn()));
    }
}
