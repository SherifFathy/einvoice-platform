package com.einvoice.api.eta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.eta.EtaItem;
import com.einvoice.core.domain.rbac.TransactionRole;
import com.einvoice.core.domain.rbac.TransactionRolePermission;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaItemRepository;
import com.einvoice.core.repository.rbac.TransactionRolePermissionRepository;
import com.einvoice.core.repository.rbac.TransactionRoleRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import java.math.BigDecimal;
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
class EtaItemMultiCompanyListIT {

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
    @Autowired private EtaItemRepository etaItemRepository;
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
                .nameEn("Alpha Co").nameAr("ألفا").taxNumber("MULTI_ITEM_A").isActive(true).build());
        companyAId = companyA.getId();

        Company companyB = companyRepository.save(Company.builder()
                .nameEn("Beta Co").nameAr("بيتا").taxNumber("MULTI_ITEM_B").isActive(true).build());
        companyBId = companyB.getId();

        Company companyC = companyRepository.save(Company.builder()
                .nameEn("Gamma Co").nameAr("جاما").taxNumber("MULTI_ITEM_C").isActive(true).build());
        companyCId = companyC.getId();

        User user = User.builder()
                .name("Multi User")
                .email("multi@eta-item-list.com")
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

        seedItem(companyAId, "SKU-A1", "Item A1");
        seedItem(companyAId, "SKU-A2", "Item A2");
        seedItem(companyBId, "SKU-B1", "Item B1");
        seedItem(companyCId, "SKU-C1", "Item C1");
    }

    @AfterEach
    void tearDown() {
        etaItemRepository.deleteAll();
        uctrRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private void seedViewerRoleAndAssignment(User user, Company company) {
        TransactionRole viewerRole = transactionRoleRepository
                .findByAuthorityAndTransactionTypeAndRoleCode("ETA", "ITEMS", "VIEWER")
                .orElseGet(() -> {
                    TransactionRole role = transactionRoleRepository.save(TransactionRole.builder()
                            .authority("ETA")
                            .transactionType("ITEMS")
                            .roleCode("VIEWER")
                            .description("Test ETA Items Viewer")
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
                .transactionType("ITEMS")
                .roleCode("VIEWER")
                .isActive(true)
                .build());
    }

    private void seedItem(UUID companyId, String internalCode, String nameEn) {
        etaItemRepository.save(EtaItem.builder()
                .companyId(companyId)
                .authorityEnvironmentId(ETA_PREPROD_ENV)
                .internalCode(internalCode)
                .itemType("EGS")
                .itemCode("EG-" + internalCode)
                .nameEn(nameEn)
                .unitPrice(new BigDecimal("100.00"))
                .taxRate(new BigDecimal("14.00"))
                .isActive(true)
                .build());
    }

    @Test
    void noCompanyFilter_returnsAllAssignedCompanies() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyAId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void filterByCompanyA_returnsOnlyA() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyAId)
                        .param("companyId", companyAId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void filterByCompanyB_returnsOnlyB() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyAId)
                        .param("companyId", companyBId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Item B1"));
    }

    @Test
    void filterByUnassignedCompanyC_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyAId)
                        .param("companyId", companyCId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void companyCData_neverLeaksIntoAssignedResults() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyAId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.nameEn=='Item C1')]").doesNotExist());
    }
}
