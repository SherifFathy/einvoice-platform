package com.einvoice.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.config.EtaConfigRepository;
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
class EtaConfigContractTest {

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
    @Autowired private EtaConfigRepository etaConfigRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Config Test Co").nameAr("شركة").taxNumber("CFG001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Config Admin")
                .email("admin@eta-config-contract.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", (short) 2, companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        etaConfigRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void read_returns200WithNullFieldsWhenNeverConfigured() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.id").isEmpty())
                .andExpect(jsonPath("$.clientId").isEmpty())
                .andExpect(jsonPath("$.clientSecret1").isEmpty())
                .andExpect(jsonPath("$.clientSecret2").isEmpty())
                .andExpect(jsonPath("$.submissionUrl").isEmpty())
                .andExpect(jsonPath("$.tokenUrl").isEmpty());
    }

    @Test
    void replace_returns200WithPersistedBody() throws Exception {
        Map<String, Object> body = Map.of(
                "clientId", "abc-client",
                "clientSecret1", "secret1",
                "clientSecret2", "secret2",
                "tokenUrl", "https://token.example.com",
                "submissionUrl", "https://submit.example.com");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.clientId").value("abc-client"))
                .andExpect(jsonPath("$.clientSecret1").value("secret1"))
                .andExpect(jsonPath("$.clientSecret2").value("secret2"))
                .andExpect(jsonPath("$.tokenUrl").value("https://token.example.com"))
                .andExpect(jsonPath("$.submissionUrl").value("https://submit.example.com"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.branchId").isEmpty());
    }

    @Test
    void replace_roundTripAfterGet() throws Exception {
        Map<String, Object> body = Map.of(
                "clientId", "round-trip-client",
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url",
                "submissionUrl", "https://sub.url",
                "tokenName", "tn",
                "tokenPass", "tp");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("round-trip-client"))
                .andExpect(jsonPath("$.tokenName").value("tn"))
                .andExpect(jsonPath("$.tokenPass").value("tp"));
    }

    @Test
    void replace_branchIdInBody_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "clientId", "abc",
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url",
                "submissionUrl", "https://sub.url",
                "branchId", UUID.randomUUID().toString());

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRANCH_ID_NOT_ALLOWED"));
    }

    @Test
    void replace_missingRequiredField_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url",
                "submissionUrl", "https://sub.url");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void replace_missingSubmissionUrl_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "clientId", "abc",
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void read_unauthorizedContext_returns401() throws Exception {
        UUID wrongCompany = UUID.randomUUID();
        mockMvc.perform(get("/api/companies/{companyId}/eta/config", wrongCompany)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_CONTEXT"));
    }

    @Test
    void read_adminMode_returns403CompanyContextRequired() throws Exception {
        User superUser = User.builder()
                .name("Super")
                .email("super@eta-config-contract.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        String adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void replace_secondPutUpdatesSameRow() throws Exception {
        Map<String, Object> body1 = Map.of(
                "clientId", "first-client",
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url",
                "submissionUrl", "https://sub.url");

        String response1 = mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(response1).get("id").asText();
        String firstCreatedAt = objectMapper.readTree(response1).get("createdAt").asText();

        Map<String, Object> body2 = Map.of(
                "clientId", "second-client",
                "clientSecret1", "s1-new",
                "clientSecret2", "s2-new",
                "tokenUrl", "https://new-token.url",
                "submissionUrl", "https://new-sub.url");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.clientId").value("second-client"))
                .andExpect(jsonPath("$.createdAt").value(firstCreatedAt));
    }

    @Test
    void replace_productionEnv_missingTokenName_returns400() throws Exception {
        Company prodCompany = companyRepository.save(Company.builder()
                .nameEn("Prod Co").nameAr("شركة").taxNumber("PROD001").isActive(true).build());

        User prodUser = User.builder()
                .name("Prod Admin")
                .email("admin@eta-config-prod.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        prodUser = userRepository.save(prodUser);
        String prodToken = jwtTokenProvider.createToken(
                prodUser.getId(), prodUser.getEmail(), true,
                "ETA", "PRODUCTION", (short) 1, prodCompany.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        Map<String, Object> body = Map.of(
                "clientId", "prod-client",
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url",
                "submissionUrl", "https://sub.url");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", prodCompany.getId())
                        .header("Authorization", "Bearer " + prodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PRODUCTION_TOKEN_FIELDS"))
                .andExpect(jsonPath("$.details.field").value("tokenName"));
    }

    @Test
    void replace_productionEnv_withTokenFields_succeeds() throws Exception {
        Company prodCompany = companyRepository.save(Company.builder()
                .nameEn("Prod Co 2").nameAr("شركة").taxNumber("PROD002").isActive(true).build());

        User prodUser = User.builder()
                .name("Prod Admin 2")
                .email("admin2@eta-config-prod.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        prodUser = userRepository.save(prodUser);
        String prodToken = jwtTokenProvider.createToken(
                prodUser.getId(), prodUser.getEmail(), true,
                "ETA", "PRODUCTION", (short) 1, prodCompany.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        Map<String, Object> body = Map.of(
                "clientId", "prod-client",
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url",
                "submissionUrl", "https://sub.url",
                "tokenName", "my-token",
                "tokenPass", "my-pass");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", prodCompany.getId())
                        .header("Authorization", "Bearer " + prodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("prod-client"));
    }
}
