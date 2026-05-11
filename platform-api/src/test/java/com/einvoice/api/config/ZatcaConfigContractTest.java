package com.einvoice.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.config.ZatcaConfigRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.core.repository.zatca.ZatcaChainStateRepository;
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
class ZatcaConfigContractTest {

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
    @Autowired private ZatcaConfigRepository zatcaConfigRepository;
    @Autowired private ZatcaChainStateRepository zatcaChainStateRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("ZATCA Config Test Co").nameAr("شركة").taxNumber("ZCFG001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("ZATCA Config Admin")
                .email("admin@zatca-config-contract.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5, companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        zatcaChainStateRepository.deleteAll();
        zatcaConfigRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void read_returns200WithNullFieldsWhenNeverConfigured() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.id").isEmpty())
                .andExpect(jsonPath("$.privateKey").isEmpty())
                .andExpect(jsonPath("$.deviceUuid").isEmpty())
                .andExpect(jsonPath("$.csr").isEmpty())
                .andExpect(jsonPath("$.complianceCertificate").isEmpty())
                .andExpect(jsonPath("$.complianceApiSecret").isEmpty())
                .andExpect(jsonPath("$.chainStateInitialized").value(false))
                .andExpect(jsonPath("$.isActive").isEmpty());
    }

    @Test
    void replace_returns200WithPersistedBody() throws Exception {
        Map<String, Object> body = Map.of(
                "privateKey", "-----BEGIN PRIVATE KEY-----\ntest-key\n-----END PRIVATE KEY-----",
                "deviceUuid", "device-uuid-001",
                "csr", "-----BEGIN CERTIFICATE REQUEST-----\ntest-csr\n-----END CERTIFICATE REQUEST-----",
                "complianceCertificate", "-----BEGIN CERTIFICATE-----\ncompliance-cert\n-----END CERTIFICATE-----",
                "complianceApiSecret", "compliance-secret");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.privateKey").value(
                        "-----BEGIN PRIVATE KEY-----\ntest-key\n-----END PRIVATE KEY-----"))
                .andExpect(jsonPath("$.deviceUuid").value("device-uuid-001"))
                .andExpect(jsonPath("$.csr").value(
                        "-----BEGIN CERTIFICATE REQUEST-----\ntest-csr\n-----END CERTIFICATE REQUEST-----"))
                .andExpect(jsonPath("$.complianceCertificate").value(
                        "-----BEGIN CERTIFICATE-----\ncompliance-cert\n-----END CERTIFICATE-----"))
                .andExpect(jsonPath("$.complianceApiSecret").value("compliance-secret"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.chainStateInitialized").value(true));
    }

    @Test
    void replace_roundTripAfterGet() throws Exception {
        Map<String, Object> body = Map.of(
                "privateKey", "key-data",
                "deviceUuid", "device-123",
                "csr", "csr-data",
                "complianceCertificate", "comp-cert",
                "complianceApiSecret", "comp-secret",
                "productionCertificate", "prod-cert",
                "productionApiSecret", "prod-secret",
                "certificateExpiryDate", "2026-12-31");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateKey").value("key-data"))
                .andExpect(jsonPath("$.productionCertificate").value("prod-cert"))
                .andExpect(jsonPath("$.certificateExpiryDate").value("2026-12-31"))
                .andExpect(jsonPath("$.chainStateInitialized").value(true));
    }

    @Test
    void replace_branchIdInBody_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "privateKey", "key-data",
                "deviceUuid", "device-123",
                "csr", "csr-data",
                "complianceCertificate", "comp-cert",
                "complianceApiSecret", "comp-secret",
                "branchId", UUID.randomUUID().toString());

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRANCH_ID_NOT_ALLOWED"));
    }

    @Test
    void replace_missingRequiredField_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "deviceUuid", "device-123",
                "csr", "csr-data",
                "complianceCertificate", "comp-cert",
                "complianceApiSecret", "comp-secret");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void replace_missingComplianceApiSecret_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "privateKey", "key-data",
                "deviceUuid", "device-123",
                "csr", "csr-data",
                "complianceCertificate", "comp-cert");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void read_unauthorizedContext_returns401() throws Exception {
        UUID wrongCompany = UUID.randomUUID();
        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", wrongCompany)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_CONTEXT"));
    }

    @Test
    void read_adminMode_returns403CompanyContextRequired() throws Exception {
        User superUser = User.builder()
                .name("Super")
                .email("super@zatca-config-contract.com")
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

    @Test
    void replace_secondPutUpdatesSameRow() throws Exception {
        Map<String, Object> body1 = Map.of(
                "privateKey", "key-1",
                "deviceUuid", "dev-1",
                "csr", "csr-1",
                "complianceCertificate", "cert-1",
                "complianceApiSecret", "secret-1");

        String response1 = mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(response1).get("id").asText();
        String firstCreatedAt = objectMapper.readTree(response1).get("createdAt").asText();

        Map<String, Object> body2 = Map.of(
                "privateKey", "key-2",
                "deviceUuid", "dev-2",
                "csr", "csr-2",
                "complianceCertificate", "cert-2",
                "complianceApiSecret", "secret-2");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.privateKey").value("key-2"))
                .andExpect(jsonPath("$.createdAt").value(firstCreatedAt));
    }

    @Test
    void replace_chainStateInitializedPresentInResponse() throws Exception {
        Map<String, Object> body = Map.of(
                "privateKey", "key-data",
                "deviceUuid", "device-123",
                "csr", "csr-data",
                "complianceCertificate", "comp-cert",
                "complianceApiSecret", "comp-secret");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainStateInitialized").value(true));
    }

    @Test
    void replace_malformedExpiryDate_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "privateKey", "key-data",
                "deviceUuid", "device-123",
                "csr", "csr-data",
                "complianceCertificate", "comp-cert",
                "complianceApiSecret", "comp-secret",
                "certificateExpiryDate", "not-a-date");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
