package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.User;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserRepository;
import com.einvoice.core.service.UserService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class LastSuperUserGuardIT {

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

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserCompanyRoleRepository userCompanyRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User soleSuperUser;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        soleSuperUser = User.builder()
                .name("Only Super User")
                .email("sole-super@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isActive(true)
                .isSuperUser(true)
                .build();
        soleSuperUser = userRepository.save(soleSuperUser);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(soleSuperUser.getId(), "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_USER"))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        userCompanyRoleRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void deactivateLastSuperUser_throwsIllegalStateException() {
        assertThatThrownBy(() -> userService.deactivateUser(soleSuperUser.getId(), soleSuperUser.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last active Super User");
    }

    @Test
    void deleteLastSuperUser_throwsIllegalStateException() {
        assertThatThrownBy(() -> userService.deleteUser(soleSuperUser.getId(), soleSuperUser.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last active Super User");
    }
}
