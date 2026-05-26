package com.einvoice.api.zatca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ZatcaItemSearchIT {

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

    private UUID companyId;
    private String zatcaSandboxToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Search Co").nameAr("شركة البحث").taxNumber("SCH004").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Search User")
                .email("search@zatca-item.com")
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
        zatcaItemRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private Map<String, Object> itemPayload(String nameEn, String nameAr, String internalCode) {
        return Map.of(
                "nameEn", nameEn,
                "nameAr", nameAr,
                "internalCode", internalCode,
                "vatCategory", "S",
                "vatRate", 15.0,
                "unitPrice", 100.0);
    }

    @Test
    void searchByEnglishName_returnsMatchingItems() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Laptop Pro", "لابتوب برو", "SKU-Z001"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Desktop Max", "ديسكتوب", "SKU-Z002"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "laptop")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Laptop Pro"));
    }

    @Test
    void searchByArabicName_returnsMatchingItems() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Laptop Pro", "لابتوب برو", "SKU-Z010"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Desktop Max", "ديسكتوب ماكس", "SKU-Z011"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "لابتوب")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Laptop Pro"));
    }

    @Test
    void searchByInternalCode_returnsMatchingItems() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Laptop Pro", "لابتوب", "SKU-Z020"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Desktop Max", "ديسكتوب", "SKU-Z021"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "SKU-Z020")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].internalCode").value("SKU-Z020"));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("LAPTOP PRO", "لابتوب", "SKU-Z030"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "laptop")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("LAPTOP PRO"));
    }

    @Test
    void searchDoesNotLeakAcrossScopes() throws Exception {
        UUID userId = userRepository.findAll().get(0).getId();
        String zatcaProdToken = jwtTokenProvider.createToken(
                userId, "search@zatca-item.com", true,
                "ZATCA", "PRODUCTION", (short) 3, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Sandbox Laptop", "لابتوب ساند", "SKU-Z040"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Prod Laptop", "لابتوب برود", "SKU-Z041"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "laptop")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Sandbox Laptop"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "laptop")
                        .header("Authorization", "Bearer " + zatcaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Prod Laptop"));
    }

    @Test
    void searchWithNoMatch_returnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Laptop Pro", "لابتوب", "SKU-Z050"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "zzzzznonexistent")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.total").value(0));
    }

    @Test
    void searchMatchesMultipleFieldsSimultaneously() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Alpha Laptop", "ألفا", "SKU-Z060"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Beta Desktop", "بيتا", "SKU-ALPHA-Z061"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "alpha")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchExcludesInactiveByDefault() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nameEn", "Active Laptop", "nameAr", "نشط",
                                        "internalCode", "SKU-Z-ACTIVE", "vatCategory", "S",
                                        "vatRate", 15.0, "unitPrice", 100.0, "isActive", true))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nameEn", "Inactive Laptop", "nameAr", "غير نشط",
                                        "internalCode", "SKU-Z-INACTIVE", "vatCategory", "S",
                                        "vatRate", 15.0, "unitPrice", 100.0, "isActive", false))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "laptop")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Active Laptop"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "laptop")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchWithCompanyFilter_returnsOnlyMatchingCompany() throws Exception {
        Company companyB = companyRepository.save(Company.builder()
                .nameEn("Other Co").nameAr("أخرى").taxNumber("SCH004B").isActive(true).build());
        UUID userId = userRepository.findAll().get(0).getId();
        String tokenForB = jwtTokenProvider.createToken(
                userId, "search@zatca-item.com", true,
                "ZATCA", "SANDBOX", (short) 5, companyB.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Acme Laptop", "لابتوب", "SKU-Z070"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{cid}/zatca/items", companyB.getId())
                        .header("Authorization", "Bearer " + tokenForB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                itemPayload("Acme Desktop", "لابتوب فرعي", "SKU-Z071"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyId)
                        .param("q", "acme")
                        .param("companyId", companyId.toString())
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Acme Laptop"));
    }
}
