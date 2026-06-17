package com.einvoice.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.auth.dto.LoginRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegularUserLoginIT {

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

    private User user;
    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .nameEn("Login IT Co")
                .nameAr("شركة اختبار تسجيل الدخول")
                .taxNumber("300000000000400")
                .isActive(true)
                .build();
        company = companyRepository.save(company);

        user = User.builder()
                .name("Login IT User")
                .email("user@login-it.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(user).company(company)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE").roleCode("ACCOUNTANT")
                .isActive(true).build());
    }

    @AfterEach
    void tearDown() {
        uctrRepo.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void acceptance1_successfulLogin_jwtIssued() throws Exception {
        LoginRequest request = new LoginRequest(
                "user@login-it.com", "Password1",
                "ETA", "PREPROD", company.getId());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("mode").asText()).isEqualTo("OPERATIONAL_MODE");
    }

    @Test
    void acceptance2_sessionContextPayloadCorrect_singleCompany() throws Exception {
        String token = loginAndGetToken();

        MvcResult ctxResult = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ctx = objectMapper.readTree(ctxResult.getResponse().getContentAsString());
        assertThat(ctx.get("companies").size()).isEqualTo(1);
        assertThat(ctx.get("companies").get(0).get("companyNameEn").asText()).isEqualTo("Login IT Co");
    }

    @Test
    void acceptance3_missingCompanyId_succeedsWithAuthorityScoped() throws Exception {
        LoginRequest request = new LoginRequest(
                "user@login-it.com", "Password1",
                "ETA", "PREPROD", null);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("mode").asText()).isEqualTo("AUTHORITY_SCOPED");
    }

    @Test
    void acceptance4_noAssignments_unauthorizedContext() throws Exception {
        Company otherCompany = Company.builder()
                .nameEn("Other Co")
                .nameAr("أخرى")
                .taxNumber("300000000000401")
                .isActive(true)
                .build();
        otherCompany = companyRepository.save(otherCompany);

        LoginRequest request = new LoginRequest(
                "user@login-it.com", "Password1",
                "ETA", "PREPROD", otherCompany.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptance5_sessionContextEnvA_excludesEnvBCompanies() throws Exception {
        Company envBCompany = Company.builder()
                .nameEn("Env B Co")
                .nameAr("شركة بيئة ب")
                .taxNumber("300000000000500")
                .isActive(true)
                .build();
        envBCompany = companyRepository.save(envBCompany);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(user).company(envBCompany)
                .authorityEnvironmentId((short) 5)
                .transactionType("STANDARD").roleCode("ACCOUNTANT")
                .isActive(true).build());

        String token = loginAndGetToken();

        MvcResult ctxResult = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ctx = objectMapper.readTree(ctxResult.getResponse().getContentAsString());
        assertThat(ctx.get("companies").size()).isEqualTo(1);
        assertThat(ctx.get("companies").get(0).get("companyNameEn").asText()).isEqualTo("Login IT Co");
    }

    private String loginAndGetToken() throws Exception {
        LoginRequest request = new LoginRequest(
                "user@login-it.com", "Password1",
                "ETA", "PREPROD", company.getId());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
