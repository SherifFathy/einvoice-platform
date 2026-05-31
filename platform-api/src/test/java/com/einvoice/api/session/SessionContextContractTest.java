package com.einvoice.api.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
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
class SessionContextContractTest {

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

    private User etaUser;
    private User zatcaUser;
    private Company etaCompany;
    private Company zatcaCompany;

    @BeforeEach
    void setUp() {
        etaCompany = Company.builder()
                .nameEn("ETA Co")
                .nameAr("شركة ETA")
                .taxNumber("300000000000200")
                .isActive(true)
                .build();
        etaCompany = companyRepository.save(etaCompany);

        zatcaCompany = Company.builder()
                .nameEn("ZATCA Co")
                .nameAr("شركة ZATCA")
                .taxNumber("300000000000300")
                .isActive(true)
                .build();
        zatcaCompany = companyRepository.save(zatcaCompany);

        etaUser = User.builder()
                .name("ETA User")
                .email("eta@session-contract.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        etaUser = userRepository.save(etaUser);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(etaUser).company(etaCompany)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE").roleCode("ACCOUNTANT")
                .isActive(true).build());

        zatcaUser = User.builder()
                .name("ZATCA User")
                .email("zatca@session-contract.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        zatcaUser = userRepository.save(zatcaUser);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(zatcaUser).company(zatcaCompany)
                .authorityEnvironmentId((short) 4)
                .transactionType("STANDARD").roleCode("ACCOUNTANT")
                .isActive(true).build());
    }

    @AfterEach
    void tearDown() {
        uctrRepo.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void etaSession_hasInvoiceReceiptModules() throws Exception {
        String token = jwtTokenProvider.createToken(
                etaUser.getId(), etaUser.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                etaCompany.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginContext.authority").value("ETA"))
                .andExpect(jsonPath("$.companies[0].modules.invoice").exists())
                .andExpect(jsonPath("$.companies[0].modules.receipt").exists())
                .andExpect(jsonPath("$.companies[0].modules.standard").doesNotExist());
    }

    @Test
    void zatcaSession_hasStandardSimplifiedModules() throws Exception {
        String token = jwtTokenProvider.createToken(
                zatcaUser.getId(), zatcaUser.getEmail(), false,
                "ZATCA", "PRODUCTION", (short) 4,
                zatcaCompany.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginContext.authority").value("ZATCA"))
                .andExpect(jsonPath("$.companies[0].modules.standard").exists())
                .andExpect(jsonPath("$.companies[0].modules.simplified").exists())
                .andExpect(jsonPath("$.companies[0].modules.invoice").doesNotExist());
    }

    @Test
    void adminMode_returnsEmptyCompanies() throws Exception {
        User su = User.builder()
                .name("Admin SU")
                .email("admin-su@session-contract.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        su = userRepository.save(su);

        String token = jwtTokenProvider.createToken(
                su.getId(), su.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("ADMIN_MODE"))
                .andExpect(jsonPath("$.companies").isEmpty());
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/session/context"))
                .andExpect(status().isUnauthorized());
    }
}
