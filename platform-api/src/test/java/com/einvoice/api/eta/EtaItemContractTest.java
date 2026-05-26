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
import com.einvoice.core.repository.eta.EtaItemRepository;
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
class EtaItemContractTest {

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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Test Co").nameAr("شركة").taxNumber("ETA_ITEM_CONTRACT").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Admin User")
                .email("admin@eta-item-contract.com")
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
        etaItemRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void listItems_returns200() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").isNotEmpty());
    }

    @Test
    void createItem_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-001",
                "itemType", "EGS",
                "itemCode", "EG-1234567",
                "nameEn", "Widget",
                "unitPrice", 100.0,
                "taxType", "T1",
                "taxSubtype", "V009",
                "taxRate", 14.0,
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.internalCode").value("SKU-001"))
                .andExpect(jsonPath("$.itemType").value("EGS"))
                .andExpect(jsonPath("$.itemCode").value("EG-1234567"))
                .andExpect(jsonPath("$.nameEn").value("Widget"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createItem_missingInternalCode_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "itemType", "EGS",
                "itemCode", "EG-001",
                "nameEn", "Test");

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_missingNameEn_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-002",
                "itemType", "EGS",
                "itemCode", "EG-002");

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_invalidItemType_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-003",
                "itemType", "INVALID",
                "itemCode", "EG-003",
                "nameEn", "Test Item");

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ITEM_TYPE"));
    }

    @Test
    void getItem_returns200() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-010",
                "itemType", "EGS",
                "itemCode", "EG-010",
                "nameEn", "Get Me");

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/companies/{companyId}/eta/items/{itemId}", companyId, itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Get Me"));
    }

    @Test
    void getItem_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/eta/items/{itemId}",
                                companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_returns200() throws Exception {
        Map<String, Object> createBody = Map.of(
                "internalCode", "SKU-020",
                "itemType", "EGS",
                "itemCode", "EG-020",
                "nameEn", "Original");

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(createResponse).get("id").asText();

        Map<String, Object> updateBody = Map.of(
                "internalCode", "SKU-020",
                "itemType", "GS1",
                "itemCode", "EG-020-U",
                "nameEn", "Updated");

        mockMvc.perform(put("/api/companies/{companyId}/eta/items/{itemId}", companyId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Updated"))
                .andExpect(jsonPath("$.itemType").value("GS1"));
    }

    @Test
    void deactivateItem_viaUpdate_returns200() throws Exception {
        Map<String, Object> createBody = Map.of(
                "internalCode", "SKU-030",
                "itemType", "EGS",
                "itemCode", "EG-030",
                "nameEn", "To Deactivate",
                "isActive", true);

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(createResponse).get("id").asText();

        Map<String, Object> deactivateBody = Map.of(
                "internalCode", "SKU-030",
                "itemType", "EGS",
                "itemCode", "EG-030",
                "nameEn", "To Deactivate",
                "isActive", false);

        mockMvc.perform(put("/api/companies/{companyId}/eta/items/{itemId}", companyId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void deleteItem_returns204() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-040",
                "itemType", "EGS",
                "itemCode", "EG-040",
                "nameEn", "To Delete");

        String createResponse = mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(delete("/api/companies/{companyId}/eta/items/{itemId}", companyId, itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteItem_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/companies/{companyId}/eta/items/{itemId}",
                                companyId, UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedContext_returns401() throws Exception {
        UUID wrongCompany = UUID.randomUUID();
        mockMvc.perform(get("/api/companies/{companyId}/eta/items", wrongCompany)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminMode_returns403() throws Exception {
        User superUser = User.builder()
                .name("Super")
                .email("super@eta-item-contract.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        String adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void createItem_duplicateInternalCode_returns409() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-DUP",
                "itemType", "EGS",
                "itemCode", "EG-DUP1",
                "nameEn", "First",
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        Map<String, Object> duplicateBody = Map.of(
                "internalCode", "SKU-DUP",
                "itemType", "EGS",
                "itemCode", "EG-DUP2",
                "nameEn", "Second",
                "isActive", true);

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_INTERNAL_CODE_IN_CONTEXT"));
    }

    @Test
    void createItem_negativeUnitPrice_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-NEG",
                "itemType", "EGS",
                "itemCode", "EG-NEG",
                "nameEn", "Negative Price",
                "unitPrice", -10.0);

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_negativeTaxRate_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "internalCode", "SKU-NEGT",
                "itemType", "EGS",
                "itemCode", "EG-NEGT",
                "nameEn", "Negative Tax",
                "taxRate", -5.0);

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
