package com.einvoice.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.admin.dto.AssignmentCreateRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AdminAssignmentContractTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("einvoice_test").withUsername("test").withPassword("test");

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
    @Autowired private UserCompanyTransactionRoleRepository uctrRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String superUserToken;
    private User superUser;
    private User regularUser;
    private Company company;

    @BeforeEach
    void setUp() {
        superUser = userRepository.save(User.builder()
                .name("SU").email("super@asgn-contract.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());
        superUserToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);

        regularUser = userRepository.save(User.builder()
                .name("Regular").email("regular@asgn-contract.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(false).isActive(true).build());

        company = companyRepository.save(Company.builder()
                .nameEn("Assign Co").nameAr("تعيين").taxNumber("555").isActive(true).build());
    }

    @AfterEach
    void tearDown() {
        uctrRepo.deleteAll();
        companyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listAssignments_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}/assignments", regularUser.getId())
                        .header("Authorization", "Bearer " + superUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createAssignment_returns201() throws Exception {
        AssignmentCreateRequest request = new AssignmentCreateRequest(
                company.getId(), (short) 2, "INVOICE", "ACCOUNTANT");

        mockMvc.perform(post("/api/admin/users/{id}/assignments", regularUser.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.authorityEnvironmentId").value(2))
                .andExpect(jsonPath("$.transactionType").value("INVOICE"))
                .andExpect(jsonPath("$.roleCode").value("ACCOUNTANT"));
    }

    @Test
    void createAssignment_invalidRoleForAuthority_returns400() throws Exception {
        AssignmentCreateRequest request = new AssignmentCreateRequest(
                company.getId(), (short) 4, "RECEIPT", "ACCOUNTANT");

        mockMvc.perform(post("/api/admin/users/{id}/assignments", regularUser.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE_FOR_AUTHORITY"));
    }

    @Test
    void createAssignment_duplicate_returns409() throws Exception {
        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(regularUser).company(company)
                .authorityEnvironmentId((short) 2).transactionType("INVOICE")
                .roleCode("ACCOUNTANT").isActive(true).build());

        AssignmentCreateRequest request = new AssignmentCreateRequest(
                company.getId(), (short) 2, "INVOICE", "ACCOUNTANT");

        mockMvc.perform(post("/api/admin/users/{id}/assignments", regularUser.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_EXISTS"));
    }

    @Test
    void createAssignment_taxNumberDuplicateInContext_returns400() throws Exception {
        Company duplicateTaxCompany = companyRepository.save(Company.builder()
                .nameEn("Duplicate Tax Co").nameAr("ضريبة مكررة")
                .taxNumber(company.getTaxNumber())
                .isActive(true).build());

        User otherUser = userRepository.save(User.builder()
                .name("Other").email("other@asgn-contract.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(false).isActive(true).build());

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(otherUser).company(company)
                .authorityEnvironmentId((short) 2).transactionType("INVOICE")
                .roleCode("ACCOUNTANT").isActive(true).build());

        AssignmentCreateRequest request = new AssignmentCreateRequest(
                duplicateTaxCompany.getId(), (short) 2, "INVOICE", "ACCOUNTANT");

        mockMvc.perform(post("/api/admin/users/{id}/assignments", regularUser.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TAX_NUMBER_DUPLICATE_IN_CONTEXT"));
    }

    @Test
    void deleteAssignment_returns204() throws Exception {
        UserCompanyTransactionRole assignment = uctrRepo.save(
                UserCompanyTransactionRole.builder()
                        .user(regularUser).company(company)
                        .authorityEnvironmentId((short) 2).transactionType("INVOICE")
                        .roleCode("VIEWER").isActive(true).build());

        mockMvc.perform(delete("/api/admin/users/{id}/assignments/{aid}",
                        regularUser.getId(), assignment.getId())
                        .header("Authorization", "Bearer " + superUserToken))
                .andExpect(status().isNoContent());
    }
}
