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
import com.einvoice.core.repository.zatca.ZatcaItemRepository;
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
class ZatcaItemContractTest {

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
    @Autowired private ZatcaItemRepository zatcaItemRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Test Co").nameAr("شركة").taxNumber("ZATCA_ITEM_CONTRACT").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Admin User")
                .email("admin@zatca-item-contract.com")
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
        zatcaItemRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void listItems_returns200() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").isNotEmpty());
    }

    @Test
    void createItem_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-Z001",
                "nameEn", "Widget",
                "vatCategory", "S",
                "vatRate", 15.0,
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.internalCode").value("SKU-Z001"))
                .andExpect(jsonPath("$.vatCategory").value("S"))
                .andExpect(jsonPath("$.vatRate").value(15.0))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createItem_missingInternalCode_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "nameEn", "Test",
                "vatCategory", "S");

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_invalidVatCategory_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-Z002",
                "nameEn", "Test",
                "vatCategory", "X");

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VAT_CATEGORY"));
    }

    @Test
    void createItem_nonZeroVatRateForNonS_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-Z003",
                "nameEn", "Test",
                "vatCategory", "Z",
                "vatRate", 5.0);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VAT_RATE"));
    }

    @Test
    void createItem_zeroVatRateForNonS_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-Z004",
                "nameEn", "Exempt Item",
                "vatCategory", "E",
                "vatRate", 0.0);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vatCategory").value("E"));
    }

    @Test
    void getItem_returns200() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-Z010",
                "nameEn", "Get Me",
                "vatCategory", "S",
                "vatRate", 15.0);

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items/{itemId}", companyId, itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Get Me"));
    }

    @Test
    void getItem_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/zatca/items/{itemId}",
                                companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_returns200() throws Exception {
        Map<String, Object> createBody = Map.of(
                "internalCode", "SKU-Z020",
                "nameEn", "Original",
                "vatCategory", "S",
                "vatRate", 15.0);

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(createResponse).get("id").asText();

        Map<String, Object> updateBody = Map.of(
                "internalCode", "SKU-Z020",
                "nameEn", "Updated",
                "vatCategory", "O",
                "vatRate", 0.0);

        mockMvc.perform(put("/api/companies/{companyId}/zatca/items/{itemId}", companyId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Updated"))
                .andExpect(jsonPath("$.vatCategory").value("O"));
    }

    @Test
    void deleteItem_returns204() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-Z040",
                "nameEn", "To Delete",
                "vatCategory", "S",
                "vatRate", 15.0);

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(delete("/api/companies/{companyId}/zatca/items/{itemId}", companyId, itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteItem_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/companies/{companyId}/zatca/items/{itemId}",
                                companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminMode_returns403() throws Exception {
        User superUser = User.builder()
                .name("Super")
                .email("super@zatca-item-contract.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        String adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5, null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void createItem_duplicateInternalCode_returns409() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-ZDUP",
                "nameEn", "First",
                "vatCategory", "S",
                "vatRate", 15.0,
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        Map<String, Object> duplicateBody = Map.of(
                "internalCode", "SKU-ZDUP",
                "nameEn", "Second",
                "vatCategory", "S",
                "vatRate", 15.0,
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_INTERNAL_CODE_IN_CONTEXT"));
    }

    @Test
    void createItem_negativeUnitPrice_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-ZNEG",
                "nameEn", "Negative Price",
                "vatCategory", "S",
                "vatRate", 15.0,
                "unitPrice", -10.0);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_negativeVatRate_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-ZNEGR",
                "nameEn", "Negative Rate",
                "vatCategory", "S",
                "vatRate", -5.0);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
