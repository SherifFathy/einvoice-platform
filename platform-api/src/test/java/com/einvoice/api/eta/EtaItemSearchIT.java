package com.einvoice.api.eta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class EtaItemSearchIT {

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

    private UUID companyId;
    private String etaPreprodToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Search Co").nameAr("شركة البحث").taxNumber("SCH003").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Search User")
                .email("search@eta-item.com")
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
        etaItemRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private Map<String, Object> itemPayload(String nameEn, String nameAr, String internalCode) {
        return Map.of(
                "nameEn", nameEn,
                "nameAr", nameAr,
                "internalCode", internalCode,
                "itemType", "EGS",
                "itemCode", internalCode + "-CODE",
                "unitPrice", 10.0,
                "taxType", "V1",
                "taxSubtype", "V001",
                "taxRate", 14.0);
    }

    @Test
    void searchByEnglishName_returnsMatchingItems() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Widget Pro", "ودجيت برو", "SKU-001"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Gadget Max", "جادجيت ماكس", "SKU-002"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "widget")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Widget Pro"));
    }

    @Test
    void searchByArabicName_returnsMatchingItems() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Widget Pro", "ودجيت برو", "SKU-010"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Gadget Max", "جادجيت ماكس", "SKU-011"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "ودجيت")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Widget Pro"));
    }

    @Test
    void searchByInternalCode_returnsMatchingItems() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Widget Pro", "ودجيت", "SKU-020"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Gadget Max", "جادجيت", "SKU-021"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "SKU-020")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].internalCode").value("SKU-020"));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("WIDGET PRO", "ودجيت", "SKU-030"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "widget")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("WIDGET PRO"));
    }

    @Test
    void searchDoesNotLeakAcrossScopes() throws Exception {
        UUID userId = userRepository.findAll().get(0).getId();
        String etaProdToken = jwtTokenProvider.createToken(
                userId, "search@eta-item.com", true,
                "ETA", "PRODUCTION", (short) 1, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Preprod Widget", "ودجيت برود", "SKU-040"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Prod Widget", "ودجيت برود", "SKU-041"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "widget")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Preprod Widget"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "widget")
                        .header("Authorization", "Bearer " + etaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Prod Widget"));
    }

    @Test
    void searchWithNoMatch_returnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Widget Pro", "ودجيت", "SKU-050"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "zzzzznonexistent")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.total").value(0));
    }

    @Test
    void searchMatchesMultipleFieldsSimultaneously() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Alpha Widget", "ألفا", "SKU-060"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Beta Gadget", "بيتا", "SKU-ALPHA-061"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "alpha")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchExcludesInactiveByDefault() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nameEn", "Active Widget", "nameAr", "نشط",
                                        "internalCode", "SKU-ACTIVE", "itemType", "EGS",
                                        "itemCode", "CODE-ACTIVE", "unitPrice", 10.0,
                                        "taxType", "V1", "taxSubtype", "V001", "taxRate", 14.0,
                                        "isActive", true))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nameEn", "Inactive Widget", "nameAr", "غير نشط",
                                        "internalCode", "SKU-INACTIVE", "itemType", "EGS",
                                        "itemCode", "CODE-INACTIVE", "unitPrice", 10.0,
                                        "taxType", "V1", "taxSubtype", "V001", "taxRate", 14.0,
                                        "isActive", false))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "widget")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Active Widget"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "widget")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchWithCompanyFilter_returnsOnlyMatchingCompany() throws Exception {
        Company companyB = companyRepository.save(Company.builder()
                .nameEn("Other Co").nameAr("أخرى").taxNumber("SCH003B").isActive(true).build());
        UUID userId = userRepository.findAll().get(0).getId();
        String tokenForB = jwtTokenProvider.createToken(
                userId, "search@eta-item.com", true,
                "ETA", "PREPROD", (short) 2, companyB.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Acme Widget", "ودجيت", "SKU-070"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{cid}/eta/items", companyB.getId())
                        .header("Authorization", "Bearer " + tokenForB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Acme Gadget", "ودجيت فرعي", "SKU-071"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyId)
                        .param("q", "acme")
                        .param("companyId", companyId.toString())
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Acme Widget"));
    }
}
