package com.einvoice.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.admin.dto.CompanyCreateRequest;
import com.einvoice.api.admin.dto.CompanyUpdateRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AdminCompanyContractTest {

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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String superUserToken;

    @BeforeEach
    void setUp() {
        User superUser = User.builder()
                .name("Super User")
                .email("super@company-contract.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
        superUserToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);
    }

    @AfterEach
    void tearDown() {
        companyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listCompanies_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + superUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createCompany_returns201() throws Exception {
        CompanyCreateRequest request = new CompanyCreateRequest(
                "Test Co", "شركة اختبار", "100200300", null);

        mockMvc.perform(post("/api/admin/companies")
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.nameEn").value("Test Co"))
                .andExpect(jsonPath("$.nameAr").value("شركة اختبار"))
                .andExpect(jsonPath("$.taxNumber").value("100200300"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createCompany_missingNameEn_returns400() throws Exception {
        String body = """
                {"nameAr":"شركة","taxNumber":"100200300"}
                """;

        mockMvc.perform(post("/api/admin/companies")
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateCompany_returns200() throws Exception {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Old Name").nameAr("قديم").taxNumber("100").isActive(true).build());

        CompanyUpdateRequest request = new CompanyUpdateRequest(
                "New Name", "جديد", "100", null);

        mockMvc.perform(put("/api/admin/companies/{id}", company.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("New Name"));
    }

    @Test
    void deactivateCompany_returns204() throws Exception {
        Company company = companyRepository.save(Company.builder()
                .nameEn("To Deactivate").nameAr("إلغاء").taxNumber("200").isActive(true).build());

        mockMvc.perform(put("/api/admin/companies/{id}/deactivate", company.getId())
                        .header("Authorization", "Bearer " + superUserToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void createCompany_withoutSuperUser_returns403() throws Exception {
        User regular = User.builder()
                .name("Regular").email("regular@company-contract.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(false).isActive(true).build();
        regular = userRepository.save(regular);
        String token = jwtTokenProvider.createToken(
                regular.getId(), regular.getEmail(), false,
                "ETA", "PREPROD", (short) 2, UUID.randomUUID(), TenantContext.Mode.OPERATIONAL_MODE);

        CompanyCreateRequest request = new CompanyCreateRequest("A", "B", "C", null);

        mockMvc.perform(post("/api/admin/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
