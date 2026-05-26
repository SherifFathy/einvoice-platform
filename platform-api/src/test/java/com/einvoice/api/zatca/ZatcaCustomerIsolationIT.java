package com.einvoice.api.zatca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaCustomerRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.core.repository.zatca.ZatcaCustomerRepository;
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
class ZatcaCustomerIsolationIT {

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
    @Autowired private ZatcaCustomerRepository zatcaCustomerRepository;
    @Autowired private EtaCustomerRepository etaCustomerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private UUID companyId;
    private String zatcaSandboxToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("ZATCA Iso Co").nameAr("شركة").taxNumber("ZISO001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Iso User")
                .email("iso@zatca-customer-isolation.com")
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
        zatcaCustomerRepository.deleteAll();
        etaCustomerRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private Map<String, Object> zatcaCustomerPayload(String name, String vatNumber) {
        return Map.of(
                "nameEn", name,
                "customerType", "B",
                "vatNumber", vatNumber,
                "addressData", Map.of(
                        "streetName", "St", "buildingNumber", "1",
                        "city", "Riyadh", "postalCode", "11111",
                        "districtName", "Dist", "country", "SA"));
    }

    @Test
    void customersAreIsolatedAcrossZatcaEnvironments() throws Exception {
        String zatcaProdToken = jwtTokenProvider.createToken(
                userRepository.findAll().get(0).getId(),
                "iso@zatca-customer-isolation.com", true,
                "ZATCA", "PRODUCTION", (short) 3, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                zatcaCustomerPayload("Sandbox Cust", "300020001000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                zatcaCustomerPayload("Prod Cust", "300020002000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Sandbox Cust"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Prod Cust"));
    }

    @Test
    void customersAreIsolatedAcrossZatcaAndEta() throws Exception {
        String etaToken = jwtTokenProvider.createToken(
                userRepository.findAll().get(0).getId(),
                "iso@zatca-customer-isolation.com", true,
                "ETA", "PREPROD", (short) 2, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                zatcaCustomerPayload("ZATCA Cust", "300020001000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void randomizedFuzz_50Probes_noCrossContextLeakage() throws Exception {
        UUID userId = userRepository.findAll().get(0).getId();
        String zatcaProdToken = jwtTokenProvider.createToken(
                userId, "iso@zatca-customer-isolation.com", true,
                "ZATCA", "PRODUCTION", (short) 3, companyId, TenantContext.Mode.OPERATIONAL_MODE);
        String etaToken = jwtTokenProvider.createToken(
                userId, "iso@zatca-customer-isolation.com", true,
                "ETA", "PREPROD", (short) 2, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        Random rng = new Random(42);
        int sandboxCount = 0;
        int prodCount = 0;

        for (int i = 0; i < 50; i++) {
            String name = "ZFuzz-" + i;
            String vat = "3" + String.format("%013d", i) + "3";
            String token;
            int choice = rng.nextInt(3);

            if (choice == 0) {
                token = zatcaSandboxToken;
                sandboxCount++;
            } else if (choice == 1) {
                token = zatcaProdToken;
                prodCount++;
            } else {
                mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                                .header("Authorization", "Bearer " + etaToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "nameEn", name, "customerType", "B",
                                        "taxNumber", "ETAX" + i,
                                        "addressData", Map.of(
                                                "country", "EG", "governorate", "Cairo",
                                                "regionCity", "Nasr City", "street", "10",
                                                "buildingNumber", "12")))))
                        .andExpect(status().isCreated());
                continue;
            }

            mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(zatcaCustomerPayload(name, vat))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(sandboxCount));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(prodCount));
    }
}
