package com.einvoice.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class JwtClaimsContractIT {

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

    private static final Set<String> ALLOWED_CLAIMS = Set.of(
            "sub", "iat", "exp", "jti", "email", "isSuperUser", "authority",
            "environment", "authorityEnvironmentId", "companyId", "mode");

    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .nameEn("JWT Claims Co")
                .nameAr("شركة مطالبات JWT")
                .taxNumber("999000000000200")
                .isActive(true)
                .build();
        company = companyRepository.save(company);

        user = User.builder()
                .name("JWT Claims User")
                .email("jwt-claims@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        user = userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void operationalToken_containsOnlyAllowedClaims() {
        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                company.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        Claims claims = jwtTokenProvider.parseToken(token);

        for (String key : claims.keySet()) {
            assertThat(ALLOWED_CLAIMS).as("unexpected claim '%s' in JWT", key).contains(key);
        }

        assertThat(claims.keySet()).contains("sub", "email", "isSuperUser", "authority",
                "environment", "authorityEnvironmentId", "companyId", "mode");
    }

    @Test
    void operationalToken_doesNotContainForbiddenClaims() {
        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                company.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        Claims claims = jwtTokenProvider.parseToken(token);

        assertThat(claims.keySet()).doesNotContain("permissions", "roles", "assignments");
    }

    @Test
    void adminModeToken_doesNotContainForbiddenClaims() {
        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                null, TenantContext.Mode.ADMIN_MODE);

        Claims claims = jwtTokenProvider.parseToken(token);

        assertThat(claims.keySet()).doesNotContain("permissions", "roles", "assignments");
        assertThat(claims.get("companyId")).isNull();
    }

    @Test
    void operationalToken_claimValuesAreCorrect() {
        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                "ETA", "PREPROD", (short) 2,
                company.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        Claims claims = jwtTokenProvider.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo(user.getEmail());
        assertThat(claims.get("isSuperUser", Boolean.class)).isFalse();
        assertThat(claims.get("authority", String.class)).isEqualTo("ETA");
        assertThat(claims.get("environment", String.class)).isEqualTo("PREPROD");
        assertThat(claims.get("mode", String.class)).isEqualTo("OPERATIONAL_MODE");
    }
}
