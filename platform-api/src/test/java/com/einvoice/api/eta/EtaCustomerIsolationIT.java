package com.einvoice.api.eta;

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
class EtaCustomerIsolationIT {

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
    @Autowired private EtaCustomerRepository etaCustomerRepository;
    @Autowired private ZatcaCustomerRepository zatcaCustomerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private UUID companyId;
    private String etaPreprodToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Iso Co").nameAr("شركة").taxNumber("ISO001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Iso User")
                .email("iso@eta-customer-isolation.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        etaPreprodToken = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", (short) 2, companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        etaCustomerRepository.deleteAll();
        zatcaCustomerRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private Map<String, Object> customerPayload(String name, String taxNumber) {
        return Map.of(
                "nameEn", name,
                "customerType", "B",
                "taxNumber", taxNumber,
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"));
    }

    @Test
    void customersAreIsolatedAcrossEtaEnvironments() throws Exception {
        String etaProdToken = jwtTokenProvider.createToken(
                userRepository.findAll().get(0).getId(),
                "iso@eta-customer-isolation.com", true,
                "ETA", "PRODUCTION", (short) 1, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerPayload("Preprod Cust", "111"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerPayload("Prod Cust", "222"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Preprod Cust"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Prod Cust"));
    }

    @Test
    void customersAreIsolatedAcrossEtaAndZatca() throws Exception {
        String zatcaToken = jwtTokenProvider.createToken(
                userRepository.findAll().get(0).getId(),
                "iso@eta-customer-isolation.com", true,
                "ZATCA", "SANDBOX", (short) 5, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerPayload("ETA Cust", "333"))))
                .andExpect(status().isCreated());

        Map<String, Object> zatcaBody = Map.of(
                "nameEn", "ZATCA Cust",
                "customerType", "B",
                "vatNumber", "300020001000003",
                "addressData", Map.of(
                        "streetName", "St", "buildingNumber", "1",
                        "city", "Riyadh", "postalCode", "11111",
                        "districtName", "Dist", "country", "SA"));

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("ETA Cust"));
    }

    @Test
    void sameTaxNumberReusableAcrossDifferentEnvironments() throws Exception {
        String etaProdToken = jwtTokenProvider.createToken(
                userRepository.findAll().get(0).getId(),
                "iso@eta-customer-isolation.com", true,
                "ETA", "PRODUCTION", (short) 1, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerPayload("Cust A", "TAX999"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerPayload("Cust B", "TAX999"))))
                .andExpect(status().isCreated());
    }

    @Test
    void randomizedFuzz_50Probes_noCrossContextLeakage() throws Exception {
        UUID userId = userRepository.findAll().get(0).getId();
        String etaProdToken = jwtTokenProvider.createToken(
                userId, "iso@eta-customer-isolation.com", true,
                "ETA", "PRODUCTION", (short) 1, companyId, TenantContext.Mode.OPERATIONAL_MODE);
        String zatcaToken = jwtTokenProvider.createToken(
                userId, "iso@eta-customer-isolation.com", true,
                "ZATCA", "SANDBOX", (short) 5, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        Random rng = new Random(42);
        int etaPreprodCount = 0;
        int etaProdCount = 0;
        int zatcaCount = 0;

        for (int i = 0; i < 50; i++) {
            String name = "Fuzz-" + i;
            String tax = "FUZZ" + String.format("%05d", i);
            String token;
            String url;

            int choice = rng.nextInt(3);
            if (choice == 0) {
                token = etaPreprodToken;
                url = "/api/companies/{companyId}/eta/customers";
                etaPreprodCount++;
            } else if (choice == 1) {
                token = etaProdToken;
                url = "/api/companies/{companyId}/eta/customers";
                etaProdCount++;
            } else {
                token = zatcaToken;
                url = "/api/companies/{companyId}/zatca/customers";
                zatcaCount++;
            }

            if (choice < 2) {
                mockMvc.perform(post(url, companyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(customerPayload(name, tax))))
                        .andExpect(status().isCreated());
            } else {
                Map<String, Object> zatcaBody = Map.of(
                        "nameEn", name, "customerType", "B",
                        "vatNumber", "3" + String.format("%013d", i) + "3",
                        "addressData", Map.of(
                                "streetName", "St", "buildingNumber", "1",
                                "city", "Riyadh", "postalCode", "11111",
                                "districtName", "Dist", "country", "SA"));
                mockMvc.perform(post(url, companyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(zatcaBody)))
                        .andExpect(status().isCreated());
            }
        }

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(etaPreprodCount));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(etaProdCount));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(zatcaCount));
    }
}
