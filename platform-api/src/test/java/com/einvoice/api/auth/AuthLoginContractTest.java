package com.einvoice.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.auth.dto.LoginRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AuthLoginContractTest {

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
                .nameEn("Test Co")
                .nameAr("شركة اختبار")
                .taxNumber("300000000000003")
                .isActive(true)
                .build();
        company = companyRepository.save(company);

        regularUser = User.builder()
                .name("Regular User")
                .email("regular@login-test.com")
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
                .email("super@login-test.com")
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
    void login_regularUser_returns200WithTokenAndOperationalMode() throws Exception {
        LoginRequest request = new LoginRequest(
                "regular@login-test.com", "Password1",
                "ETA", "PREPROD", company.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.mode").value("OPERATIONAL_MODE"));
    }

    @Test
    void login_superUserWithoutCompany_returns200WithAdminMode() throws Exception {
        LoginRequest request = new LoginRequest(
                "super@login-test.com", "Password1",
                "ETA", "PREPROD", null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("ADMIN_MODE"));
    }

    @Test
    void login_badPassword_returns401BadCredentials() throws Exception {
        LoginRequest request = new LoginRequest(
                "regular@login-test.com", "WrongPassword",
                "ETA", "PREPROD", company.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void login_unknownEmail_returns401BadCredentials() throws Exception {
        LoginRequest request = new LoginRequest(
                "nobody@login-test.com", "Password1",
                "ETA", "PREPROD", company.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void login_regularUserMissingCompany_returns401CompanyContextRequired() throws Exception {
        LoginRequest request = new LoginRequest(
                "regular@login-test.com", "Password1",
                "ETA", "PREPROD", null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void login_regularUserNoAssignment_returns401UnauthorizedContext() throws Exception {
        Company otherCompany = Company.builder()
                .nameEn("Other Co")
                .nameAr("شركة أخرى")
                .taxNumber("300000000000099")
                .isActive(true)
                .build();
        otherCompany = companyRepository.save(otherCompany);

        LoginRequest request = new LoginRequest(
                "regular@login-test.com", "Password1",
                "ETA", "PREPROD", otherCompany.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_CONTEXT"));
    }

    @Test
    void login_invalidAuthorityEnvironment_returns400() throws Exception {
        LoginRequest request = new LoginRequest(
                "regular@login-test.com", "Password1",
                "ETA", "NONEXISTENT", company.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_missingEmail_returns400ValidationError() throws Exception {
        String body = """
                {"password":"Password1","authority":"ETA","environment":"PREPROD","companyId":"%s"}
                """.formatted(company.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
