package com.einvoice.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ZatcaConfigPermissionIT {

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
                .nameEn("ZATCA Perm Co").nameAr("شركة").taxNumber("ZPCFG001").isActive(true).build());
        companyId = company.getId();
    }

    @AfterEach
    void tearDown() {
        uctrRepository.deleteAll();
    }

    @Test
    void viewerCannotReadConfig() throws Exception {
        User viewer = User.builder()
                .name("Viewer")
                .email("viewer@zatca-config-perm.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        viewer = userRepository.save(viewer);

        seedRoleWithNoConfigAccess(viewer, companyId, "VIEWER");

        String viewerToken = jwtTokenProvider.createToken(
                viewer.getId(), viewer.getEmail(), false,
                "ZATCA", "SANDBOX", (short) 5, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountantCannotReadOrWriteConfig() throws Exception {
        User accountant = User.builder()
                .name("Accountant")
                .email("accountant@zatca-config-perm.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        accountant = userRepository.save(accountant);

        seedRoleWithNoConfigAccess(accountant, companyId, "ACCOUNTANT");

        String accountantToken = jwtTokenProvider.createToken(
                accountant.getId(), accountant.getEmail(), false,
                "ZATCA", "SANDBOX", (short) 5, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isForbidden());

        Map<String, Object> body = Map.of(
                "privateKey", "key-data",
                "deviceUuid", "device-123",
                "csr", "csr-data",
                "complianceCertificate", "comp-cert",
                "complianceApiSecret", "comp-secret");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void companyAdminCanReadAndWriteConfig() throws Exception {
        User admin = User.builder()
                .name("Admin")
                .email("admin@zatca-config-perm.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        admin = userRepository.save(admin);

        seedConfigAdminRole(admin, companyId);

        String adminToken = jwtTokenProvider.createToken(
                admin.getId(), admin.getEmail(), false,
                "ZATCA", "SANDBOX", (short) 5, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Map<String, Object> body = Map.of(
                "privateKey", "admin-key",
                "deviceUuid", "admin-device",
                "csr", "admin-csr",
                "complianceCertificate", "admin-cert",
                "complianceApiSecret", "admin-secret");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateKey").value("admin-key"));
    }

    @Test
    void adminMode_rejectedWithCompanyContextRequired() throws Exception {
        User superUser = User.builder()
                .name("Super")
                .email("super@zatca-config-perm.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        String adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5, null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    private void seedRoleWithNoConfigAccess(User user, UUID compId, String roleCode) {
        TransactionRole customersRole = transactionRoleRepository
                .findByAuthorityAndTransactionTypeAndRoleCode("ZATCA", "CUSTOMERS", roleCode)
                .orElseGet(() -> {
                    TransactionRole r = transactionRoleRepository.save(TransactionRole.builder()
                            .authority("ZATCA")
                            .transactionType("CUSTOMERS")
                            .roleCode(roleCode)
                            .description("ZATCA " + roleCode)
                            .build());
                    transactionRolePermissionRepository.save(TransactionRolePermission.builder()
                            .transactionRole(r)
                            .permissionCode("VIEW")
                            .build());
                    return r;
                });

        uctrRepository.save(UserCompanyTransactionRole.builder()
                .user(user)
                .company(companyRepository.findById(compId).orElseThrow())
                .authorityEnvironmentId((short) 5)
                .transactionType("CUSTOMERS")
                .roleCode(roleCode)
                .isActive(true)
                .build());
    }

    private void seedConfigAdminRole(User user, UUID compId) {
        TransactionRole role = transactionRoleRepository
                .findByAuthorityAndTransactionTypeAndRoleCode("ZATCA", "CONFIG", "ADMIN")
                .orElseGet(() -> {
                    TransactionRole r = transactionRoleRepository.save(TransactionRole.builder()
                            .authority("ZATCA")
                            .transactionType("CONFIG")
                            .roleCode("ADMIN")
                            .description("ZATCA Config Admin")
                            .build());
                    transactionRolePermissionRepository.save(TransactionRolePermission.builder()
                            .transactionRole(r)
                            .permissionCode("VIEW")
                            .build());
                    transactionRolePermissionRepository.save(TransactionRolePermission.builder()
                            .transactionRole(r)
                            .permissionCode("EDIT")
                            .build());
                    return r;
                });

        uctrRepository.save(UserCompanyTransactionRole.builder()
                .user(user)
                .company(companyRepository.findById(compId).orElseThrow())
                .authorityEnvironmentId((short) 5)
                .transactionType("CONFIG")
                .roleCode("ADMIN")
                .isActive(true)
                .build());
    }
}
