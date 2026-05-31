package com.einvoice.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.auth.dto.LoginRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
class NextLoginPermissionRefreshIT {

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
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserCompanyTransactionRoleRepository uctrRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private User regularUser;
    private User superUser;
    private Company companyA;
    private Company companyB;

    @BeforeEach
    void setUp() {
        companyA = Company.builder()
                .nameEn("Refresh Co A")
                .nameAr("شركة تحديث أ")
                .taxNumber("999000000000400")
                .isActive(true)
                .build();
        companyA = companyRepository.save(companyA);

        companyB = Company.builder()
                .nameEn("Refresh Co B")
                .nameAr("شركة تحديث ب")
                .taxNumber("999000000000401")
                .isActive(true)
                .build();
        companyB = companyRepository.save(companyB);

        regularUser = User.builder()
                .name("Refresh User")
                .email("refresh@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        regularUser = userRepository.save(regularUser);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(regularUser).company(companyA)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE").roleCode("ACCOUNTANT")
                .isActive(true).build());

        superUser = User.builder()
                .name("Refresh SU")
                .email("refresh-su@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
    }

    @AfterEach
    void tearDown() {
        uctrRepo.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void newAssignment_reflectedOnNextLogin_notMidSession() throws Exception {
        String tokenT1 = loginAsRegularUser(companyA.getId());
        JsonNode payloadP1 = getSessionContext(tokenT1);
        assertThat(payloadP1.get("companies").size()).isEqualTo(1);
        assertThat(findCompany(payloadP1, companyA.getId())).isNotNull();
        assertThat(findCompany(payloadP1, companyB.getId())).isNull();

        grantAssignmentViaAdmin();

        JsonNode payloadStillP1 = getSessionContext(tokenT1);
        assertThat(payloadStillP1.get("companies").size()).isEqualTo(1);
        assertThat(findCompany(payloadStillP1, companyB.getId())).isNull();

        String tokenT2 = loginAsRegularUser(companyA.getId());
        JsonNode payloadP2 = getSessionContext(tokenT2);
        assertThat(payloadP2.get("companies").size()).isEqualTo(2);
        assertThat(findCompany(payloadP2, companyB.getId())).isNotNull();

        JsonNode companyBNode = findCompany(payloadP2, companyB.getId());
        assertThat(companyBNode.get("modules").get("invoice").get("visible").asBoolean()).isTrue();
    }

    private String loginAsRegularUser(UUID companyId) throws Exception {
        LoginRequest request = new LoginRequest(
                "refresh@test.com", "pass",
                "ETA", "PREPROD", companyId);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private JsonNode getSessionContext(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void grantAssignmentViaAdmin() throws Exception {
        String suToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        String body = """
                {
                    "companyId": "%s",
                    "authorityEnvironmentId": 2,
                    "transactionType": "INVOICE",
                    "roleCode": "VIEWER"
                }
                """.formatted(companyB.getId());

        mockMvc.perform(post("/api/admin/users/" + regularUser.getId() + "/assignments")
                        .header("Authorization", "Bearer " + suToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private JsonNode findCompany(JsonNode payload, UUID companyId) {
        JsonNode companies = payload.get("companies");
        for (JsonNode c : companies) {
            if (companyId.toString().equals(c.get("companyId").asText())) {
                return c;
            }
        }
        return null;
    }
}
