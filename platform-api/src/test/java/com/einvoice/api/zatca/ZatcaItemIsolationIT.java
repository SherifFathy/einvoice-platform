package com.einvoice.api.zatca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaItemRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.core.repository.zatca.ZatcaItemRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Random;
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
class ZatcaItemIsolationIT {

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
    @Autowired private EtaItemRepository etaItemRepository;
    @Autowired private ZatcaItemRepository zatcaItemRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private UUID companyId;
    private String zatcaSandboxToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Iso Co").nameAr("شركة").taxNumber("ISO_ITEM_ZATCA").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Iso User")
                .email("iso@zatca-item-isolation.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        zatcaSandboxToken = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5, companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        etaItemRepository.deleteAll();
        zatcaItemRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private Map<String, Object> zatcaItemPayload(String internalCode, String name) {
        return Map.of(
                "internalCode", internalCode,
                "nameEn", name,
                "vatCategory", "S",
                "vatRate", 15.0);
    }

    private Map<String, Object> etaItemPayload(String internalCode, String name) {
        return Map.of(
                "internalCode", internalCode,
                "itemType", "EGS",
                "itemCode", "EG-" + internalCode,
                "nameEn", name);
    }

    @Test
    void itemsAreIsolatedAcrossZatcaEnvironments() throws Exception {
        String zatcaProdToken = jwtTokenProvider.createToken(
                userRepository.findAll().get(0).getId(),
                "iso@zatca-item-isolation.com", true,
                "ZATCA", "PRODUCTION", (short) 3, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaItemPayload("SKU-SB", "Sandbox Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaItemPayload("SKU-PR", "Prod Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Sandbox Item"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Prod Item"));
    }

    @Test
    void itemsAreIsolatedAcrossZatcaAndEta() throws Exception {
        String etaToken = jwtTokenProvider.createToken(
                userRepository.findAll().get(0).getId(),
                "iso@zatca-item-isolation.com", true,
                "ETA", "PREPROD", (short) 2, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaItemPayload("SKU-Z", "ZATCA Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaItemPayload("SKU-E", "ETA Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("ZATCA Item"));
    }

    @Test
    void sameInternalCodeReusableAcrossDifferentEnvironments() throws Exception {
        String zatcaProdToken = jwtTokenProvider.createToken(
                userRepository.findAll().get(0).getId(),
                "iso@zatca-item-isolation.com", true,
                "ZATCA", "PRODUCTION", (short) 3, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaItemPayload("SKU-SHARED", "Item A"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaItemPayload("SKU-SHARED", "Item B"))))
                .andExpect(status().isCreated());
    }

    @Test
    void randomizedFuzz_50Probes_noCrossContextLeakage() throws Exception {
        UUID userId = userRepository.findAll().get(0).getId();
        String zatcaProdToken = jwtTokenProvider.createToken(
                userId, "iso@zatca-item-isolation.com", true,
                "ZATCA", "PRODUCTION", (short) 3, companyId, TenantContext.Mode.OPERATIONAL_MODE);
        String etaToken = jwtTokenProvider.createToken(
                userId, "iso@zatca-item-isolation.com", true,
                "ETA", "PREPROD", (short) 2, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        Random rng = new Random(42);
        int sandboxCount = 0;
        int prodCount = 0;
        int etaCount = 0;

        for (int i = 0; i < 50; i++) {
            String code = "FUZZ-" + i;
            String name = "Fuzz-" + i;
            String token;
            String url;

            int choice = rng.nextInt(3);
            if (choice == 0) {
                token = zatcaSandboxToken;
                url = "/api/companies/{companyId}/zatca/items";
                sandboxCount++;
            } else if (choice == 1) {
                token = zatcaProdToken;
                url = "/api/companies/{companyId}/zatca/items";
                prodCount++;
            } else {
                token = etaToken;
                url = "/api/companies/{companyId}/eta/items";
                etaCount++;
            }

            if (choice < 2) {
                mockMvc.perform(post(url, companyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(zatcaItemPayload(code, name))))
                        .andExpect(status().isCreated());
            } else {
                mockMvc.perform(post(url, companyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(etaItemPayload(code, name))))
                        .andExpect(status().isCreated());
            }
        }

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(sandboxCount));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(prodCount));
    }
}
