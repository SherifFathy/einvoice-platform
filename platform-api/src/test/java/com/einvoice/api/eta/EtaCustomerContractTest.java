package com.einvoice.api.eta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaCustomerRepository;
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
class EtaCustomerContractTest {

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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Test Co").nameAr("شركة").taxNumber("100200300").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Admin User")
                .email("admin@eta-customer-contract.com")
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
        etaCustomerRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void listCustomers_returns200() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").isNotEmpty());
    }

    @Test
    void createCustomer_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Acme LLC",
                "customerType", "B",
                "taxNumber", "100200300",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10 Kornish",
                        "buildingNumber", "12"),
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.nameEn").value("Acme LLC"))
                .andExpect(jsonPath("$.customerType").value("B"))
                .andExpect(jsonPath("$.taxNumber").value("100200300"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createCustomer_missingNameEn_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10 Kornish",
                        "buildingNumber", "12"));

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createCustomer_missingAddressData_returns400() throws Exception {
        Map<String, Object> body = Map.of("nameEn", "Test Customer");

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCustomer_invalidCustomerType_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Test",
                "customerType", "INVALID",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "1"));

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CUSTOMER_TYPE"));
    }

    @Test
    void getCustomer_returns200() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Get Me",
                "customerType", "B",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"));

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String customerId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers/{customerId}", companyId, customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Get Me"));
    }

    @Test
    void getCustomer_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/customers/{customerId}",
                                companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCustomer_returns200() throws Exception {
        Map<String, Object> createBody = Map.of(
                "nameEn", "Original",
                "customerType", "B",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"));

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
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
                        "country", "EG", "governorate", "Giza",
                        "regionCity", "Downtown", "street", "20",
                        "buildingNumber", "5"));

        mockMvc.perform(put("/api/companies/{companyId}/eta/customers/{customerId}", companyId, customerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Updated"))
                .andExpect(jsonPath("$.customerType").value("P"));
    }

    @Test
    void deactivateCustomer_viaUpdate_returns200() throws Exception {
        Map<String, Object> createBody = Map.of(
                "nameEn", "To Deactivate",
                "customerType", "B",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"),
                "isActive", true);

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String customerId = objectMapper.readTree(createResponse).get("id").asText();

        Map<String, Object> deactivateBody = Map.of(
                "nameEn", "To Deactivate",
                "customerType", "B",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"),
                "isActive", false);

        mockMvc.perform(put("/api/companies/{companyId}/eta/customers/{customerId}", companyId, customerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void deleteCustomer_returns204() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "To Delete",
                "customerType", "B",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"));

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String customerId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(delete("/api/companies/{companyId}/eta/customers/{customerId}", companyId, customerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCustomer_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/companies/{companyId}/eta/customers/{customerId}",
                                companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedContext_returns401() throws Exception {
        UUID wrongCompany = UUID.randomUUID();
        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", wrongCompany)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminMode_returns403() throws Exception {
        User superUser = User.builder()
                .name("Super")
                .email("super@eta-customer.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        String adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void createCustomer_duplicateTaxNumber_returns409() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "First",
                "customerType", "B",
                "taxNumber", "999888777",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"),
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        Map<String, Object> duplicateBody = Map.of(
                "nameEn", "Second",
                "customerType", "B",
                "taxNumber", "999888777",
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "20",
                        "buildingNumber", "5"),
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TAX_NUMBER_IN_CONTEXT"));
    }
}
