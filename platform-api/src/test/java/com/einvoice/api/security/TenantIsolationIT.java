package com.einvoice.api.security;

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
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
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
class TenantIsolationIT {

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
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserCompanyTransactionRoleRepository uctrRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    private User superUser;
    private Company etaCompany;
    private Company zatcaCompany;

    @BeforeEach
    void setUp() {
        etaCompany = Company.builder()
                .nameEn("ETA Isolation Co")
                .nameAr("شركة عزل ETA")
                .taxNumber("300000000000600")
                .isActive(true)
                .build();
        etaCompany = companyRepository.save(etaCompany);

        zatcaCompany = Company.builder()
                .nameEn("ZATCA Isolation Co")
                .nameAr("شركة عزل ZATCA")
                .taxNumber("300000000000700")
                .isActive(true)
                .build();
        zatcaCompany = companyRepository.save(zatcaCompany);

        user = User.builder()
                .name("Isolation User")
                .email("isolation@tenant-test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(user).company(etaCompany)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE").roleCode("ACCOUNTANT")
                .isActive(true).build());

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(user).company(zatcaCompany)
                .authorityEnvironmentId((short) 5)
                .transactionType("STANDARD").roleCode("ACCOUNTANT")
                .isActive(true).build());

        superUser = User.builder()
                .name("SU Isolation")
                .email("su-isolation@tenant-test.com")
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
    void env2Session_returnsOnlyEnv2Company() throws Exception {
        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.AUTHORITY_SCOPED);

        MvcResult result = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ctx = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(ctx.get("mode").asText()).isEqualTo("AUTHORITY_SCOPED");
        assertThat(ctx.get("activeCompanyId")).isNull();
        JsonNode companies = ctx.get("companies");
        assertThat(companies.size()).isEqualTo(1);
        assertThat(companies.get(0).get("companyNameEn").asText())
                .isEqualTo("ETA Isolation Co");
    }

    @Test
    void env5Session_returnsOnlyEnv5Company() throws Exception {
        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ZATCA", "SANDBOX", (short) 5,
                null, TenantContext.Mode.AUTHORITY_SCOPED);

        MvcResult result = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ctx = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(ctx.get("mode").asText()).isEqualTo("AUTHORITY_SCOPED");
        assertThat(ctx.get("activeCompanyId")).isNull();
        JsonNode companies = ctx.get("companies");
        assertThat(companies.size()).isEqualTo(1);
        assertThat(companies.get(0).get("companyNameEn").asText())
                .isEqualTo("ZATCA Isolation Co");
    }

    @Test
    void superUserEnv2Operational_seesEnv2CompaniesOnly() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.AUTHORITY_SCOPED);

        MvcResult result = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ctx = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(ctx.get("mode").asText()).isEqualTo("AUTHORITY_SCOPED");
        JsonNode companies = ctx.get("companies");
        for (JsonNode c : companies) {
            assertThat(c.get("companyNameEn").asText()).isNotEqualTo("ZATCA Isolation Co");
        }
    }

    @Test
    void authorityScopedUser_canReadCrossCompanyDocuments() throws Exception {
        Company secondCompany = Company.builder()
                .nameEn("Second ETA Co")
                .nameAr("شركة ETA الثانية")
                .taxNumber("300000000000800")
                .isActive(true)
                .build();
        secondCompany = companyRepository.save(secondCompany);
        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(user).company(secondCompany)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE").roleCode("VIEWER")
                .isActive(true).build());

        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.AUTHORITY_SCOPED);

        MvcResult result = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ctx = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode companies = ctx.get("companies");
        assertThat(companies.size()).isEqualTo(2);
        assertThat(StreamSupport.stream(companies.spliterator(), false)
                .anyMatch(c -> "ETA Isolation Co".equals(c.get("companyNameEn").asText()))).isTrue();
        assertThat(StreamSupport.stream(companies.spliterator(), false)
                .anyMatch(c -> "Second ETA Co".equals(c.get("companyNameEn").asText()))).isTrue();
    }

    @Test
    void companylessLogin_nonSuperUser_succeeds() throws Exception {
        LoginRequest request = new LoginRequest(
                user.getEmail(), "pass",
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
}
