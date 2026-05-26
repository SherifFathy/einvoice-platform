package com.einvoice.api.zatca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.TransactionRole;
import com.einvoice.core.domain.rbac.TransactionRolePermission;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.TransactionRolePermissionRepository;
import com.einvoice.core.repository.rbac.TransactionRoleRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ZatcaCustomerPermissionIT {

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
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TransactionRoleRepository transactionRoleRepository;
    @Autowired private TransactionRolePermissionRepository transactionRolePermissionRepository;
    @Autowired private UserCompanyTransactionRoleRepository uctrRepository;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("ZPerm Co").nameAr("شركة").taxNumber("ZPERM001").isActive(true).build());
        companyId = company.getId();
    }

    @AfterEach
    void tearDown() {
        uctrRepository.deleteAll();
    }

    @Test
    void viewerCanGetButCannotPostPutDelete() throws Exception {
        User viewer = User.builder()
                .name("Viewer")
                .email("viewer@zatca-customer-perm.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        viewer = userRepository.save(viewer);

        seedViewerRole(viewer, companyId);

        String viewerToken = jwtTokenProvider.createToken(
                viewer.getId(), viewer.getEmail(), false,
                "ZATCA", "SANDBOX", (short) 5, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        Map<String, Object> body = Map.of(
                "nameEn", "Blocked",
                "addressData", Map.of(
                        "streetName", "St", "buildingNumber", "1",
                        "city", "Riyadh", "postalCode", "11111",
                        "districtName", "Dist", "country", "SA"));

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/companies/{companyId}/zatca/customers/{customerId}",
                                companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/companies/{companyId}/zatca/customers/{customerId}",
                                companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminMode_rejectedWithCompanyContextRequired() throws Exception {
        User superUser = User.builder()
                .name("Super")
                .email("super@zatca-customer-perm.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        String adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5, null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    private void seedViewerRole(User user, UUID compId) {
        TransactionRole viewerRole = transactionRoleRepository
                .findByAuthorityAndTransactionTypeAndRoleCode("ZATCA", "CUSTOMERS", "VIEWER")
                .orElseGet(() -> {
                    TransactionRole role = transactionRoleRepository.save(TransactionRole.builder()
                            .authority("ZATCA")
                            .transactionType("CUSTOMERS")
                            .roleCode("VIEWER")
                            .description("Test ZATCA Customers Viewer")
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
                .company(companyRepository.findById(compId).orElseThrow())
                .authorityEnvironmentId((short) 5)
                .transactionType("CUSTOMERS")
                .roleCode("VIEWER")
                .isActive(true)
                .build());
    }
}
