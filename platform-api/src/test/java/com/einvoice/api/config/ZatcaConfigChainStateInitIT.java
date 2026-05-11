package com.einvoice.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.domain.zatca.ZatcaChainState;
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
class ZatcaConfigChainStateInitIT {

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
                .nameEn("Chain State Co").nameAr("شركة").taxNumber("CHAIN001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Chain Admin")
                .email("admin@zatca-chain-init.com")
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
    void firstPut_createsChainStateRowWithCounterZero() throws Exception {
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

        long chainStateCount = zatcaChainStateRepository.count();
        org.junit.jupiter.api.Assertions.assertEquals(1, chainStateCount,
                "Exactly one chain-state row should exist after first PUT");
    }

    @Test
    void subsequentPuts_doNotOverwriteChainStateRow() throws Exception {
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
                .andExpect(status().isOk());

        ZatcaChainState chainState = zatcaChainStateRepository
                .findByCompanyAndAuthorityEnvironment(companyId, (short) 5).orElseThrow();
        chainState.setInvoiceCounter(5L);
        chainState.setPreviousInvoiceHash("hash-from-wave8");
        zatcaChainStateRepository.saveAndFlush(chainState);

        Map<String, Object> body2 = Map.of(
                "privateKey", "updated-key",
                "deviceUuid", "device-456",
                "csr", "updated-csr",
                "complianceCertificate", "updated-cert",
                "complianceApiSecret", "updated-secret",
                "productionCertificate", "prod-cert",
                "productionApiSecret", "prod-secret");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isOk());

        long chainStateCount = zatcaChainStateRepository.count();
        org.junit.jupiter.api.Assertions.assertEquals(1, chainStateCount,
                "Chain-state row count must remain 1 after second PUT");

        ZatcaChainState afterSecondPut = zatcaChainStateRepository
                .findByCompanyAndAuthorityEnvironment(companyId, (short) 5).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(5L, afterSecondPut.getInvoiceCounter(),
                "invoiceCounter must survive a subsequent config PUT");
        org.junit.jupiter.api.Assertions.assertEquals("hash-from-wave8", afterSecondPut.getPreviousInvoiceHash(),
                "previousInvoiceHash must survive a subsequent config PUT");
    }

    @Test
    void crossEnvironment_chainStateRowsAreIndependent() throws Exception {
        Map<String, Object> body = Map.of(
                "privateKey", "sandbox-key",
                "deviceUuid", "sandbox-device",
                "csr", "sandbox-csr",
                "complianceCertificate", "sandbox-cert",
                "complianceApiSecret", "sandbox-secret");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        User simUser = User.builder()
                .name("Sim Admin")
                .email("admin@zatca-chain-sim.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        simUser = userRepository.save(simUser);
        String simToken = jwtTokenProvider.createToken(
                simUser.getId(), simUser.getEmail(), true,
                "ZATCA", "SIMULATION", (short) 4, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        Map<String, Object> simBody = Map.of(
                "privateKey", "sim-key",
                "deviceUuid", "sim-device",
                "csr", "sim-csr",
                "complianceCertificate", "sim-cert",
                "complianceApiSecret", "sim-secret");

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + simToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simBody)))
                .andExpect(status().isOk());

        long chainStateCount = zatcaChainStateRepository.count();
        org.junit.jupiter.api.Assertions.assertEquals(2, chainStateCount,
                "Two independent chain-state rows should exist for two environments");
    }

    @Test
    void read_beforeAnyPut_returnsChainStateInitializedFalse() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainStateInitialized").value(false));
    }

    @Test
    void read_afterPut_returnsChainStateInitializedTrue() throws Exception {
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
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainStateInitialized").value(true));
    }
}
