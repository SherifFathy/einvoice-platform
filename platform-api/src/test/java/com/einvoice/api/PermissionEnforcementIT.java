package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PermissionEnforcementIT {

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
    private BranchRepository branchRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCompanyRoleRepository userCompanyRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;
    private Branch branch;
    private User user;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        company = new Company();
        company.setNameAr("شركة اختبار الصلاحيات");
        company.setNameEn("Permission Test Co");
        company.setVatNumber("710000000000007");
        company.setCrNumber("CR007");
        company = companyRepository.save(company);

        branch = Branch.builder()
                .company(company)
                .nameAr("فرع رئيسي")
                .nameEn("Main Branch")
                .branchCode("BR-001")
                .lovContextId(1L)
                .city("Riyadh")
                .countryCode("SA")
                .build();
        branch = branchRepository.save(branch);

        user = User.builder()
                .name("No Create Perm User")
                .email("no-create-perm@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isActive(true)
                .build();
        user = userRepository.save(user);

        jdbcTemplate.update(
                "INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by) "
                        + "VALUES (?, ?, 1, 'VIEW_INVOICE_LIST', ?)",
                user.getId(), company.getId(), user.getId());
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
    void createInvoice_withoutCreateInvoicePermission_returns403() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(),
                company.getId(), "ACCOUNTANT",
                List.of("ZATCA_SANDBOX"),
                List.of(Map.of("id", company.getId(), "name", company.getNameEn())),
                "ZATCA_SANDBOX",
                "ZATCA", "INVOICE", "SANDBOX",
                1L,
                List.of("VIEW_INVOICE_LIST"),
                false);

        String invoiceBody = """
                {
                    "type": "STANDARD",
                    "issueDate": "2026-04-24",
                    "branchId": %d,
                    "authority": "ZATCA",
                    "lines": [{
                        "descriptionEn": "Test Line",
                        "quantity": 1,
                        "unit": "EA",
                        "unitPrice": 100.00,
                        "vatCategory": "S",
                        "vatRate": 15.00,
                        "sortOrder": 1
                    }]
                }
                """.formatted(branch.getId());

        mockMvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody))
                .andExpect(status().isForbidden());
    }
}
