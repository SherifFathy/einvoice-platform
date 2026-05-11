package com.einvoice.api.eta;

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
class EtaCustomerSearchIT {

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

    private UUID companyId;
    private String etaPreprodToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Search Co").nameAr("شركة البحث").taxNumber("SCH001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Search User")
                .email("search@eta-customer.com")
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
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private Map<String, Object> customerPayload(String nameEn, String nameAr, String taxNumber) {
        return Map.of(
                "nameEn", nameEn,
                "nameAr", nameAr,
                "customerType", "B",
                "taxNumber", taxNumber,
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10",
                        "buildingNumber", "12"));
    }

    @Test
    void searchByEnglishName_returnsMatchingCustomers() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Corporation", "أكيم", "100200300"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Beta Industries", "بيتا", "400500600"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "acm")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Acme Corporation"));
    }

    @Test
    void searchByArabicName_returnsMatchingCustomers() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Corp", "أكيم للتجارة", "111222333"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Beta Corp", "بيتا للتجارة", "444555666"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "أكيم")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Acme Corp"));
    }

    @Test
    void searchByTaxNumber_returnsMatchingCustomers() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Corp", "أكيم", "100200300"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Beta Corp", "بيتا", "400500600"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "0200")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].taxNumber").value("100200300"));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("ACME Corp", "أكيم", "999888777"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("ACME Corp"));
    }

    @Test
    void searchDoesNotLeakAcrossScopes() throws Exception {
        UUID userId = userRepository.findAll().get(0).getId();
        String etaProdToken = jwtTokenProvider.createToken(
                userId, "search@eta-customer.com", true,
                "ETA", "PRODUCTION", (short) 1, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Preprod Acme", "أكيم برود", "111111111"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Prod Acme", "أكيم برود", "222222222"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Preprod Acme"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + etaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Prod Acme"));
    }

    @Test
    void searchWithNoMatch_returnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Corp", "أكيم", "123456789"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "zzzzznonexistent")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.total").value(0));
    }

    @Test
    void searchMatchesMultipleFieldsSimultaneously() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Alpha Co", "ألفا", "555000111"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Beta Alpha Ltd", "بيتا", "555000222"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "alpha")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchExcludesInactiveByDefault() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nameEn", "Active Acme", "nameAr", "نشط",
                                        "customerType", "B", "taxNumber", "777000111",
                                        "isActive", true,
                                        "addressData", Map.of("country", "EG", "governorate", "Cairo",
                                                "regionCity", "Nasr City", "street", "10", "buildingNumber", "12")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nameEn", "Inactive Acme", "nameAr", "غير نشط",
                                        "customerType", "B", "taxNumber", "777000222",
                                        "isActive", false,
                                        "addressData", Map.of("country", "EG", "governorate", "Cairo",
                                                "regionCity", "Nasr City", "street", "10", "buildingNumber", "12")))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Active Acme"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "acme")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchWithCompanyFilter_returnsOnlyMatchingCompany() throws Exception {
        Company companyB = companyRepository.save(Company.builder()
                .nameEn("Other Co").nameAr("أخرى").taxNumber("SCH001B").isActive(true).build());
        UUID userId = userRepository.findAll().get(0).getId();
        String tokenForB = jwtTokenProvider.createToken(
                userId, "search@eta-customer.com", true,
                "ETA", "PREPROD", (short) 2, companyB.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyId)
                        .header("Authorization", "Bearer " + etaPreprodToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Primary", "أكيم", "888000111"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{cid}/eta/customers", companyB.getId())
                        .header("Authorization", "Bearer " + tokenForB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Secondary", "أكيم فرعي", "888000222"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyId)
                        .param("q", "acme")
                        .param("companyId", companyId.toString())
                        .header("Authorization", "Bearer " + etaPreprodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Acme Primary"));
    }
}
