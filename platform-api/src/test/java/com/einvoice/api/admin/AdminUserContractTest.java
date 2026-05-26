package com.einvoice.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.admin.dto.UserCreateRequest;
import com.einvoice.api.admin.dto.UserUpdateRequest;
import com.einvoice.core.domain.user.User;
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
class AdminUserContractTest {

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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String superUserToken;
    private User superUser;

    @BeforeEach
    void setUp() {
        superUser = userRepository.save(User.builder()
                .name("SU").email("super@user-contract.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());
        superUserToken = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void listUsers_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + superUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createUser_returns201() throws Exception {
        UserCreateRequest request = new UserCreateRequest(
                "Aya Accountant", "aya@user-contract.com", "Password1", false);

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Aya Accountant"))
                .andExpect(jsonPath("$.email").value("aya@user-contract.com"))
                .andExpect(jsonPath("$.isSuperUser").value(false))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createUser_duplicateEmail_returns409() throws Exception {
        UserCreateRequest request = new UserCreateRequest(
                "Dup", "super@user-contract.com", "P", false);

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void createUser_blankPassword_returns400() throws Exception {
        String body = """
                {"name":"Test","email":"test@blank.com","password":"","isSuperUser":false}
                """;

        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateUser_returns200() throws Exception {
        User user = userRepository.save(User.builder()
                .name("Old").email("old@user-contract.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(false).isActive(true).build());

        UserUpdateRequest request = new UserUpdateRequest("Updated", null, null, null);

        mockMvc.perform(put("/api/admin/users/{id}", user.getId())
                        .header("Authorization", "Bearer " + superUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void deactivateLastSuperUser_returns409() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/deactivate", superUser.getId())
                        .header("Authorization", "Bearer " + superUserToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_SUPER_USER_PROTECTED"));
    }

    @Test
    void activateUser_returns204() throws Exception {
        User inactive = userRepository.save(User.builder()
                .name("Inactive").email("inactive@user-contract.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(false).isActive(false).build());

        mockMvc.perform(put("/api/admin/users/{id}/activate", inactive.getId())
                        .header("Authorization", "Bearer " + superUserToken))
                .andExpect(status().isNoContent());
    }
}
