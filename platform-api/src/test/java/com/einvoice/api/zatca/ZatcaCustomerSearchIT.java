package com.einvoice.api.zatca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ZatcaCustomerSearchIT {

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

    private UUID companyId;
    private String zatcaSandboxToken;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Search Co").nameAr("شركة البحث").taxNumber("SCH002").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Search User")
                .email("search@zatca-customer.com")
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
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private Map<String, Object> customerPayload(String nameEn, String nameAr, String vatNumber) {
        return Map.of(
                "nameEn", nameEn,
                "nameAr", nameAr,
                "customerType", "B",
                "vatNumber", vatNumber,
                "addressData", Map.of(
                        "streetName", "King Fahd Rd", "buildingNumber", "15",
                        "city", "Riyadh", "postalCode", "12211",
                        "districtName", "Al Olaya", "country", "SA"));
    }

    @Test
    void searchByEnglishName_returnsMatchingCustomers() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Trading", "أكيم", "300010001000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Beta Trading", "بيتا", "300020002000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "acm")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Acme Trading"));
    }

    @Test
    void searchByArabicName_returnsMatchingCustomers() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Trading", "أكيم للتجارة", "300030003000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Beta Trading", "بيتا للتجارة", "300040004000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "أكيم")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Acme Trading"));
    }

    @Test
    void searchByVatNumber_returnsMatchingCustomers() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Trading", "أكيم", "300050005000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Beta Trading", "بيتا", "300060006000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "50005")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].vatNumber").value("300050005000003"));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("ACME TRADING", "أكيم", "300070007000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("ACME TRADING"));
    }

    @Test
    void searchDoesNotLeakAcrossScopes() throws Exception {
        UUID userId = userRepository.findAll().get(0).getId();
        String zatcaProdToken = jwtTokenProvider.createToken(
                userId, "search@zatca-customer.com", true,
                "ZATCA", "PRODUCTION", (short) 3, companyId, TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Sandbox Acme", "أكيم ساند", "300080008000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaProdToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Prod Acme", "أكيم برود", "300090009000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Sandbox Acme"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + zatcaProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Prod Acme"));
    }

    @Test
    void searchWithNoMatch_returnsEmptyList() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Trading", "أكيم", "300100010000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "zzzzznonexistent")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.total").value(0));
    }

    @Test
    void searchMatchesMultipleFieldsSimultaneously() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Alpha Trading", "ألفا", "300110011000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Beta Alpha Ltd", "بيتا", "300120012000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "alpha")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchExcludesInactiveByDefault() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nameEn", "Active Acme", "nameAr", "نشط",
                                        "customerType", "B", "vatNumber", "300130013000003",
                                        "isActive", true,
                                        "addressData", Map.of("streetName", "St", "buildingNumber", "1",
                                                "city", "Riyadh", "postalCode", "11111",
                                                "districtName", "Dist", "country", "SA")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nameEn", "Inactive Acme", "nameAr", "غير نشط",
                                        "customerType", "B", "vatNumber", "300140014000003",
                                        "isActive", false,
                                        "addressData", Map.of("streetName", "St", "buildingNumber", "1",
                                                "city", "Riyadh", "postalCode", "11111",
                                                "districtName", "Dist", "country", "SA")))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "acme")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Active Acme"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "acme")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void searchWithCompanyFilter_returnsOnlyMatchingCompany() throws Exception {
        Company companyB = companyRepository.save(Company.builder()
                .nameEn("Other Co").nameAr("أخرى").taxNumber("SCH002B").isActive(true).build());
        UUID userId = userRepository.findAll().get(0).getId();
        String tokenForB = jwtTokenProvider.createToken(
                userId, "search@zatca-customer.com", true,
                "ZATCA", "SANDBOX", (short) 5, companyB.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyId)
                        .header("Authorization", "Bearer " + zatcaSandboxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Primary", "أكيم", "300150015000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{cid}/zatca/customers", companyB.getId())
                        .header("Authorization", "Bearer " + tokenForB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                customerPayload("Acme Secondary", "أكيم فرعي", "300160016000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyId)
                        .param("q", "acme")
                        .param("companyId", companyId.toString())
                        .header("Authorization", "Bearer " + zatcaSandboxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Acme Primary"));
    }
}
