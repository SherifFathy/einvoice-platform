package com.einvoice.api.eta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.eta.EtaCustomer;
import com.einvoice.core.domain.rbac.TransactionRole;
import com.einvoice.core.domain.rbac.TransactionRolePermission;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaCustomerRepository;
import com.einvoice.core.repository.rbac.TransactionRolePermissionRepository;
import com.einvoice.core.repository.rbac.TransactionRoleRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class EtaCustomerMultiCompanyListIT {

    private static final short ETA_PREPROD_ENV = 2;

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
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EtaCustomerRepository etaCustomerRepository;
    @Autowired private TransactionRoleRepository transactionRoleRepository;
    @Autowired private TransactionRolePermissionRepository transactionRolePermissionRepository;
    @Autowired private UserCompanyTransactionRoleRepository uctrRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private UUID companyAId;
    private UUID companyBId;
    private UUID companyCId;
    private String token;

    @BeforeEach
    void setUp() {
        Company companyA = companyRepository.save(Company.builder()
                .nameEn("Alpha Co").nameAr("ألفا").taxNumber("MULTI_A").isActive(true).build());
        companyAId = companyA.getId();

        Company companyB = companyRepository.save(Company.builder()
                .nameEn("Beta Co").nameAr("بيتا").taxNumber("MULTI_B").isActive(true).build());
        companyBId = companyB.getId();

        Company companyC = companyRepository.save(Company.builder()
                .nameEn("Gamma Co").nameAr("جاما").taxNumber("MULTI_C").isActive(true).build());
        companyCId = companyC.getId();

        User user = User.builder()
                .name("Multi User")
                .email("multi@eta-customer-list.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        seedViewerRoleAndAssignment(user, companyA);
        seedViewerRoleAndAssignment(user, companyB);

        token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ETA", "PREPROD", ETA_PREPROD_ENV, companyAId,
                TenantContext.Mode.OPERATIONAL_MODE);

        seedCustomer(companyAId, "Customer A1", "TAX_A1");
        seedCustomer(companyAId, "Customer A2", "TAX_A2");
        seedCustomer(companyBId, "Customer B1", "TAX_B1");
        seedCustomer(companyCId, "Customer C1", "TAX_C1");
    }

    @AfterEach
    void tearDown() {
        etaCustomerRepository.deleteAll();
        uctrRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private void seedViewerRoleAndAssignment(User user, Company company) {
        TransactionRole viewerRole = transactionRoleRepository
                .findByAuthorityAndTransactionTypeAndRoleCode("ETA", "CUSTOMERS", "VIEWER")
                .orElseGet(() -> {
                    TransactionRole role = transactionRoleRepository.save(TransactionRole.builder()
                            .authority("ETA")
                            .transactionType("CUSTOMERS")
                            .roleCode("VIEWER")
                            .description("Test ETA Customers Viewer")
                            .build());
                    transactionRolePermissionRepository.save(TransactionRolePermission.builder()
                            .transactionRole(role)
                            .permissionCode("VIEW")
                            .build());
                    return role;
                });

        boolean hasPermission = transactionRolePermissionRepository
                .findByRoleId(viewerRole.getId()).stream()
                .anyMatch(p -> "VIEW".equals(p.getPermissionCode()));
        if (!hasPermission) {
            transactionRolePermissionRepository.save(TransactionRolePermission.builder()
                    .transactionRole(viewerRole)
                    .permissionCode("VIEW")
                    .build());
        }

        uctrRepository.save(UserCompanyTransactionRole.builder()
                .user(user)
                .company(company)
                .authorityEnvironmentId(ETA_PREPROD_ENV)
                .transactionType("CUSTOMERS")
                .roleCode("VIEWER")
                .isActive(true)
                .build());
    }

    private void seedCustomer(UUID companyId, String nameEn, String taxNumber) {
        etaCustomerRepository.save(EtaCustomer.builder()
                .companyId(companyId)
                .authorityEnvironmentId(ETA_PREPROD_ENV)
                .customerType("B")
                .nameEn(nameEn)
                .taxNumber(taxNumber)
                .addressData(Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"))
                .isActive(true)
                .build());
    }

    @Test
    void noCompanyFilter_returnsAllAssignedCompanies() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyAId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void filterByCompanyA_returnsOnlyA() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyAId)
                        .param("companyId", companyAId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void filterByCompanyB_returnsOnlyB() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyAId)
                        .param("companyId", companyBId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Customer B1"));
    }

    @Test
    void filterByUnassignedCompanyC_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyAId)
                        .param("companyId", companyCId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void companyCData_neverLeaksIntoAssignedResults() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyAId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.nameEn=='Customer C1')]").doesNotExist());
    }
}
