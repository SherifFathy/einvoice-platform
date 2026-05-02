package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class BranchLovContextConstraintIT {

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
    private JdbcTemplate jdbcTemplate;

    private Long companyId;

    @BeforeEach
    void setUp() {
        companyId = jdbcTemplate.queryForObject(
                "INSERT INTO companies (name_ar, name_en, vat_number, cr_number, is_active) "
                        + "VALUES ('شركة القيد', 'Constraint Test Co', '930000000000009', 'CR009', true) "
                        + "RETURNING id",
                Long.class);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM branches WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM companies WHERE id = ?", companyId);
    }

    @Test
    void insertBranchWithNullLovContextId_throwsConstraintViolation() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO branches (company_id, name_ar, name_en, branch_code, lov_context_id, is_active) "
                        + "VALUES (?, 'فرع', 'Branch', 'BR-NULL', NULL, true)",
                companyId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
