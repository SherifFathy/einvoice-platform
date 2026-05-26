package com.einvoice.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AdminModeRejectionIT {

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
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User superUser;
    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .nameEn("Admin Reject Co")
                .nameAr("شركة رفض المشرف")
                .taxNumber("999000000000100")
                .isActive(true)
                .build();
        company = companyRepository.save(company);

        superUser = User.builder()
                .name("Admin Reject SU")
                .email("admin-reject@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void adminMode_operationalStubPath_returns403CompanyContextRequired() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/companies/" + company.getId() + "/anything")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_CONTEXT_REQUIRED"));
    }

    @Test
    void adminMode_sessionContextIs_allowed() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminMode_adminEndpoints_allowed() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminMode_authEndpoints_allowed() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
