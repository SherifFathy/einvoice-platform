package com.einvoice.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.admin.dto.BranchCreateRequest;
import com.einvoice.api.admin.dto.BranchUpdateRequest;
import com.einvoice.core.domain.branch.Branch;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.branch.BranchRepository;
import com.einvoice.core.repository.company.CompanyRepository;
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
class AdminBranchContractTest {

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
    @Autowired private BranchRepository branchRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String superUserToken;
    private Company company;

    @BeforeEach
    void setUp() {
        User superUser = userRepository.save(User.builder()
                .name("SU").email("super@branch-contract.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());
        superUserToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);

        company = companyRepository.save(Company.builder()
                .nameEn("Branch Co").nameAr("فرع").taxNumber("999").isActive(true).build());
    }

    @AfterEach
    void tearDown() {
        branchRepository.deleteAll();
        companyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listBranches_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/companies/{id}/branches", company.getId())
                        .header("Authorization", "Bearer " + superUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createBranch_returns201() throws Exception {
        BranchCreateRequest request = new BranchCreateRequest(
                "Cairo HQ", "القاهرة", "CAI-01", null, null, null,
                null, null, null, null, null, null);

        mockMvc.perform(post("/api/admin/companies/{id}/branches", company.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nameEn").value("Cairo HQ"))
                .andExpect(jsonPath("$.branchCode").value("CAI-01"))
                .andExpect(jsonPath("$.companyId").value(company.getId().toString()));
    }

    @Test
    void createBranch_duplicateCode_returns400() throws Exception {
        branchRepository.save(Branch.builder().company(company)
                .nameEn("Existing").nameAr("موجود").branchCode("DUP").isActive(true).build());

        BranchCreateRequest request = new BranchCreateRequest(
                "New Branch", "جديد", "DUP", null, null, null,
                null, null, null, null, null, null);

        mockMvc.perform(post("/api/admin/companies/{id}/branches", company.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRANCH_CODE_DUPLICATE_IN_COMPANY"));
    }

    @Test
    void updateBranch_returns200() throws Exception {
        Branch branch = branchRepository.save(Branch.builder().company(company)
                .nameEn("Old").nameAr("قديم").isActive(true).build());

        BranchUpdateRequest request = new BranchUpdateRequest(
                "Updated", "محدث", null, null, null, null,
                null, null, null, null, null, null);

        mockMvc.perform(put("/api/admin/branches/{id}", branch.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Updated"));
    }
}
