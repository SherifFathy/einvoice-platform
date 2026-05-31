package com.einvoice.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.auth.dto.CompaniesRequest;
import com.einvoice.api.auth.dto.EnvironmentsRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthDiscoveryContractTest {

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
    @Autowired private UserCompanyTransactionRoleRepository uctrRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private User regularUser;
    private User superUser;
    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .nameEn("Discovery Co")
                .nameAr("شركة اكتشاف")
                .taxNumber("300000000000100")
                .isActive(true)
                .build();
        company = companyRepository.save(company);

        regularUser = User.builder()
                .name("Regular User")
                .email("regular@discovery-test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        regularUser = userRepository.save(regularUser);

        UserCompanyTransactionRole assignment = UserCompanyTransactionRole.builder()
                .user(regularUser)
                .company(company)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE")
                .roleCode("ACCOUNTANT")
                .isActive(true)
                .build();
        uctrRepo.save(assignment);

        superUser = User.builder()
                .name("Super User")
                .email("super@discovery-test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
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
    void environments_eta_returnsEtaEnvironments() throws Exception {
        EnvironmentsRequest request = new EnvironmentsRequest("ETA");
        mockMvc.perform(post("/api/auth/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments").isArray())
                .andExpect(jsonPath("$.environments[0].authority").value("ETA"));
    }

    @Test
    void environments_zatca_returnsZatcaEnvironments() throws Exception {
        EnvironmentsRequest request = new EnvironmentsRequest("ZATCA");
        mockMvc.perform(post("/api/auth/environments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments").isArray())
                .andExpect(jsonPath("$.environments[0].authority").value("ZATCA"));
    }

    @Test
    void companies_regularUser_returnsAssignedCompanies() throws Exception {
        CompaniesRequest request = new CompaniesRequest("ETA", "PREPROD", "regular@discovery-test.com");
        mockMvc.perform(post("/api/auth/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuperUser").value(false))
                .andExpect(jsonPath("$.companies").isArray())
                .andExpect(jsonPath("$.companies[0].companyId").value(company.getId().toString()));
    }

    @Test
    void companies_superUser_returnsAllActiveCompanies() throws Exception {
        CompaniesRequest request = new CompaniesRequest("ETA", "PREPROD", "super@discovery-test.com");
        mockMvc.perform(post("/api/auth/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuperUser").value(true))
                .andExpect(jsonPath("$.companies").isArray());
    }

    @Test
    void companies_unknownEmail_returnsEmptyList() throws Exception {
        CompaniesRequest request = new CompaniesRequest("ETA", "PREPROD", "nobody@discovery-test.com");
        mockMvc.perform(post("/api/auth/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies").isArray())
                .andExpect(jsonPath("$.companies").isEmpty());
    }
}
