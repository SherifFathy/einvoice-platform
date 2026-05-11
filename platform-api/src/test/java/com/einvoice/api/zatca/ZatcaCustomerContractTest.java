package com.einvoice.api.zatca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.core.repository.zatca.ZatcaCustomerRepository;
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
class ZatcaCustomerContractTest {

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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("ZATCA Co").nameAr("شركة").taxNumber("300020001000003").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Admin User")
                .email("admin@zatca-customer-contract.com")
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
        zatcaCustomerRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void listCustomers_returns200() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").isNotEmpty());
    }

    @Test
    void createCustomer_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Saudi Corp",
                "customerType", "B",
                "vatNumber", "300020001000003",
                "addressData", Map.of(
                        "streetName", "King Fahd Rd", "buildingNumber", "1234",
                        "city", "Riyadh", "postalCode", "11564",
                        "districtName", "Al-Olaya", "country", "SA"),
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.nameEn").value("Saudi Corp"))
                .andExpect(jsonPath("$.customerType").value("B"))
                .andExpect(jsonPath("$.vatNumber").value("300020001000003"));
    }

    @Test
    void createCustomer_invalidCustomerType_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Test",
                "customerType", "F",
                "addressData", Map.of(
                        "streetName", "St", "buildingNumber", "1",
                        "city", "Riyadh", "postalCode", "11111",
                        "districtName", "Dist", "country", "SA"));

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CUSTOMER_TYPE"));
    }

    @Test
    void createCustomer_invalidVatNumber_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Test",
                "customerType", "B",
                "vatNumber", "123",
                "addressData", Map.of(
                        "streetName", "St", "buildingNumber", "1",
                        "city", "Riyadh", "postalCode", "11111",
                        "districtName", "Dist", "country", "SA"));

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VAT_NUMBER"));
    }

    @Test
    void createCustomer_missingAddressKeys_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Test",
                "addressData", Map.of("country", "SA"));

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADDRESS_DATA"));
    }

    @Test
    void getCustomer_returns200() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Get Me",
                "customerType", "B",
                "vatNumber", "300020001000003",
                "addressData", Map.of(
                        "streetName", "King Fahd Rd", "buildingNumber", "1234",
                        "city", "Riyadh", "postalCode", "11564",
                        "districtName", "Al-Olaya", "country", "SA"));

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String customerId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers/{customerId}", companyId, customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Get Me"));
    }

    @Test
    void updateCustomer_returns200() throws Exception {
        Map<String, Object> createBody = Map.of(
                "nameEn", "Original",
                "customerType", "P",
                "addressData", Map.of(
                        "streetName", "St", "buildingNumber", "1",
                        "city", "Riyadh", "postalCode", "11111",
                        "districtName", "Dist", "country", "SA"));

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String customerId = objectMapper.readTree(createResponse).get("id").asText();

        Map<String, Object> updateBody = Map.of(
                "nameEn", "Updated",
                "customerType", "P",
                "addressData", Map.of(
                        "streetName", "New St", "buildingNumber", "2",
                        "city", "Jeddah", "postalCode", "22222",
                        "districtName", "New Dist", "country", "SA"));

        mockMvc.perform(put("/api/companies/{companyId}/zatca/customers/{customerId}", companyId, customerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Updated"));
    }

    @Test
    void deleteCustomer_returns204() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "To Delete",
                "customerType", "P",
                "addressData", Map.of(
                        "streetName", "St", "buildingNumber", "1",
                        "city", "Riyadh", "postalCode", "11111",
                        "districtName", "Dist", "country", "SA"));

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String customerId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(delete("/api/companies/{companyId}/zatca/customers/{customerId}", companyId, customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminMode_returns403() throws Exception {
        User superUser = User.builder()
                .name("Super")
                .email("super@zatca-customer.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        String adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5, null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void createCustomer_duplicateVatNumber_returns409() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "First",
                "customerType", "B",
                "vatNumber", "300000000000003",
                "addressData", Map.of(
                        "streetName", "King Fahd Rd", "buildingNumber", "1234",
                        "city", "Riyadh", "postalCode", "11564",
                        "districtName", "Al-Olaya", "country", "SA"),
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        Map<String, Object> duplicateBody = Map.of(
                "nameEn", "Second",
                "customerType", "B",
                "vatNumber", "300000000000003",
                "addressData", Map.of(
                        "streetName", "Other St", "buildingNumber", "5678",
                        "city", "Jeddah", "postalCode", "22222",
                        "districtName", "Dist", "country", "SA"),
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VAT_NUMBER_IN_CONTEXT"));
    }
}
