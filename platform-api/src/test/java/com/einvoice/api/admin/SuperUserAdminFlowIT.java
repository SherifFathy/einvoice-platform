package com.einvoice.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.admin.dto.AssignmentCreateRequest;
import com.einvoice.api.admin.dto.BranchCreateRequest;
import com.einvoice.api.admin.dto.CompanyCreateRequest;
import com.einvoice.api.admin.dto.UserCreateRequest;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SuperUserAdminFlowIT {

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

    private String adminToken;
    private User superUser;

    @BeforeEach
    void setUp() {
        superUser = userRepository.save(User.builder()
                .name("Super").email("super@admin-flow.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());
        adminToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);
    }

    @AfterEach
    void tearDown() {
        uctrRepo.deleteAll();
        companyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminFlow_fullOnboarding_success() throws Exception {
        MvcResult companyResult = mockMvc.perform(post("/api/admin/companies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CompanyCreateRequest("ABC Co", "شركة ABC", "100200300", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nameEn").value("ABC Co"))
                .andReturn();

        String companyId = objectMapper.readTree(companyResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/admin/companies/{id}/branches", companyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BranchCreateRequest("Cairo HQ", "القاهرة", "CAI-01",
                                        null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated());

        MvcResult userResult = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserCreateRequest("Aya Accountant", "aya@admin-flow.com", "anything", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("aya@admin-flow.com"))
                .andReturn();

        String userId = objectMapper.readTree(userResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/admin/users/{id}/assignments", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignmentCreateRequest(
                                        UUID.fromString(companyId), (short) 2, "INVOICE", "ACCOUNTANT"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/users/{id}/assignments", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionType").value("INVOICE"))
                .andExpect(jsonPath("$[0].roleCode").value("ACCOUNTANT"));
    }

    @Test
    void adminMode_operationalEndpointBlocked_returns403() throws Exception {
        mockMvc.perform(get("/api/invoices")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_sessionContextIsAllowed() throws Exception {
        mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void nextLoginPickup_newUserSeesAssignmentInSessionContext() throws Exception {
        MvcResult companyResult = mockMvc.perform(post("/api/admin/companies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CompanyCreateRequest("Pickup Co", "شركة", "999888777", null))))
                .andExpect(status().isCreated()).andReturn();
        String companyId = objectMapper.readTree(companyResult.getResponse().getContentAsString()).get("id").asText();

        MvcResult userResult = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserCreateRequest("Next Login", "nextlogin@admin-flow.com", "pass123", false))))
                .andExpect(status().isCreated()).andReturn();
        String userId = objectMapper.readTree(userResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/admin/users/{id}/assignments", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignmentCreateRequest(
                                        UUID.fromString(companyId), (short) 2, "INVOICE", "ACCOUNTANT"))))
                .andExpect(status().isCreated());

        String userToken = jwtTokenProvider.createToken(
                UUID.fromString(userId), "nextlogin@admin-flow.com", false,
                "ETA", "PREPROD", (short) 2, UUID.fromString(companyId), TenantContext.Mode.OPERATIONAL_MODE);

        mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies").isArray())
                .andExpect(jsonPath("$.companies[0].companyId").value(companyId))
                .andExpect(jsonPath("$.companies[0].modules.invoice.visible").value(true));
    }
}
