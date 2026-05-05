package com.einvoice.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.user.User;
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
class LastSuperUserInvariantIT {

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
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;
    private User su1;

    @BeforeEach
    void setUp() {
        su1 = userRepository.save(User.builder()
                .name("SU1").email("su1@invariant.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());
        token = jwtTokenProvider.createToken(
                su1.getId(), su1.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void loneSuperUser_cannotDeactivateSelf() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/deactivate", su1.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_SUPER_USER_PROTECTED"));

        User reloaded = userRepository.findById(su1.getId()).orElseThrow();
        assertThat(reloaded.getIsActive()).isTrue();
        assertThat(reloaded.getIsSuperUser()).isTrue();
    }

    @Test
    void loneSuperUser_cannotRemoveSuperUserFlag() throws Exception {
        String body = """
                {"isSuperUser": false}
                """;

        mockMvc.perform(put("/api/admin/users/{id}", su1.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_SUPER_USER_PROTECTED"));

        User reloaded = userRepository.findById(su1.getId()).orElseThrow();
        assertThat(reloaded.getIsSuperUser()).isTrue();
    }

    @Test
    void secondSuperUserExists_firstCanDeactivate() throws Exception {
        User su2 = userRepository.save(User.builder()
                .name("SU2").email("su2@invariant.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());

        mockMvc.perform(put("/api/admin/users/{id}/deactivate", su1.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(su1.getId()).orElseThrow().getIsActive()).isFalse();
        assertThat(userRepository.findById(su2.getId()).orElseThrow().getIsActive()).isTrue();
    }

    @Test
    void secondSuperUserDeactivated_thenCannotDeactivateFirst() throws Exception {
        User su2 = userRepository.save(User.builder()
                .name("SU2").email("su2@invariant.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());

        String su2Token = jwtTokenProvider.createToken(
                su2.getId(), su2.getEmail(), true,
                "ETA", "PREPROD", (short) 2, null, TenantContext.Mode.ADMIN_MODE);

        mockMvc.perform(put("/api/admin/users/{id}/deactivate", su2.getId())
                        .header("Authorization", "Bearer " + su2Token))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/admin/users/{id}/deactivate", su1.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_SUPER_USER_PROTECTED"));
    }
}
