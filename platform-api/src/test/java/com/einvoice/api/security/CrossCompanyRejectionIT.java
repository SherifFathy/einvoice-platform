package com.einvoice.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
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
class CrossCompanyRejectionIT {

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
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserCompanyTransactionRoleRepository uctrRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private User regularUser;
    private User superUser;
    private Company companyA;
    private Company companyB;

    @BeforeEach
    void setUp() {
        companyA = Company.builder()
                .nameEn("CrossA Co")
                .nameAr("شركة أ")
                .taxNumber("999000000000300")
                .isActive(true)
                .build();
        companyA = companyRepository.save(companyA);

        companyB = Company.builder()
                .nameEn("CrossB Co")
                .nameAr("شركة ب")
                .taxNumber("999000000000301")
                .isActive(true)
                .build();
        companyB = companyRepository.save(companyB);

        regularUser = User.builder()
                .name("Cross Regular")
                .email("cross-regular@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        regularUser = userRepository.save(regularUser);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(regularUser).company(companyA)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE").roleCode("ACCOUNTANT")
                .isActive(true).build());

        superUser = User.builder()
                .name("Cross SU")
                .email("cross-su@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
    }

    @AfterEach
    void tearDown() {
        uctrRepo.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void regularUser_adminEndpoint_rejected403() throws Exception {
        String token = jwtTokenProvider.createToken(
                regularUser.getId(), regularUser.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                companyA.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUser_adminEndpoint_withArbitraryCompanyId_rejected403() throws Exception {
        String token = jwtTokenProvider.createToken(
                regularUser.getId(), regularUser.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                companyA.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/admin/companies/" + companyB.getId() + "/branches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void superUser_adminEndpoint_withAnyCompanyId_allowed() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/admin/companies/" + companyA.getId() + "/branches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void superUser_adminEndpoint_allowed() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
