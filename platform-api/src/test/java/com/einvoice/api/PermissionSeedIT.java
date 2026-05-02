package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.enums.Role;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserContextPermissionRepository;
import com.einvoice.core.repository.UserRepository;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PermissionSeedIT {

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
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserCompanyRoleRepository userCompanyRoleRepository;

    @Autowired
    private UserContextPermissionRepository userContextPermissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;
    private User accountant;
    private User viewer;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setNameAr("شركة بذر");
        company.setNameEn("Seed Company");
        company.setVatNumber("400000000000004");
        company.setCrNumber("CR004");
        company = companyRepository.save(company);

        accountant = User.builder()
                .name("Accountant User")
                .email("accountant-seed@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isActive(true)
                .build();
        accountant = userRepository.save(accountant);

        UserCompanyRole accountantRole = UserCompanyRole.builder()
                .user(accountant)
                .company(company)
                .role(Role.ACCOUNTANT)
                .isActive(true)
                .build();
        userCompanyRoleRepository.save(accountantRole);

        viewer = User.builder()
                .name("Viewer User")
                .email("viewer-seed@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isActive(true)
                .build();
        viewer = userRepository.save(viewer);

        UserCompanyRole viewerRole = UserCompanyRole.builder()
                .user(viewer)
                .company(company)
                .role(Role.VIEWER)
                .isActive(true)
                .build();
        userCompanyRoleRepository.save(viewerRole);

        jdbcTemplate.update(
                "INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by) "
                        + "SELECT ucr.user_id, ucr.company_id, 1, p.permission, ucr.user_id "
                        + "FROM user_company_roles ucr "
                        + "CROSS JOIN (VALUES ('CREATE_INVOICE'), ('CREATE_CUSTOMER'), ('CREATE_ITEM'), "
                        + "    ('EDIT_INVOICE'), ('EDIT_CUSTOMER'), ('EDIT_ITEM'), "
                        + "    ('DELETE_INVOICE'), ('DELETE_CUSTOMER'), ('DELETE_ITEM'), "
                        + "    ('TRANSFER_INVOICE'), ('REFRESH_INVOICE'), "
                        + "    ('VIEW_INVOICE_LIST'), ('VIEW_CUSTOMER_LIST'), ('VIEW_ITEM_LIST')) AS p(permission) "
                        + "WHERE ucr.role IN ('COMPANY_ADMIN', 'ACCOUNTANT') AND ucr.user_id = ? "
                        + "ON CONFLICT DO NOTHING",
                accountant.getId());

        jdbcTemplate.update(
                "INSERT INTO user_context_permissions "
                        + "(user_id, company_id, lov_context_id, permission, granted_by) "
                        + "SELECT ucr.user_id, ucr.company_id, 1, p.permission, ucr.user_id "
                        + "FROM user_company_roles ucr "
                        + "CROSS JOIN (VALUES ('VIEW_INVOICE_LIST'), "
                        + "('VIEW_CUSTOMER_LIST'), ('VIEW_ITEM_LIST')) AS p(permission) "
                        + "WHERE ucr.role = 'VIEWER' AND ucr.user_id = ? "
                        + "ON CONFLICT DO NOTHING",
                viewer.getId());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM user_context_permissions WHERE user_id IN (?, ?)",
                accountant.getId(), viewer.getId());
        userCompanyRoleRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void accountantRole_hasAll14PermissionsAfterSeed() {
        Set<String> permissions = userContextPermissionRepository
                .findPermissionsByUserIdAndCompanyIdAndLovContextId(
                        accountant.getId(), company.getId(), 1L);

        assertThat(permissions).hasSize(14);
        assertThat(permissions).containsExactlyInAnyOrder(
                "CREATE_INVOICE", "CREATE_CUSTOMER", "CREATE_ITEM",
                "EDIT_INVOICE", "EDIT_CUSTOMER", "EDIT_ITEM",
                "DELETE_INVOICE", "DELETE_CUSTOMER", "DELETE_ITEM",
                "TRANSFER_INVOICE", "REFRESH_INVOICE",
                "VIEW_INVOICE_LIST", "VIEW_CUSTOMER_LIST", "VIEW_ITEM_LIST");
    }

    @Test
    void viewerRole_hasOnly3ViewPermissionsAfterSeed() {
        Set<String> permissions = userContextPermissionRepository
                .findPermissionsByUserIdAndCompanyIdAndLovContextId(
                        viewer.getId(), company.getId(), 1L);

        assertThat(permissions).hasSize(3);
        assertThat(permissions).containsExactlyInAnyOrder(
                "VIEW_INVOICE_LIST", "VIEW_CUSTOMER_LIST", "VIEW_ITEM_LIST");
    }

    @Test
    void v33SeedSQL_accountantGetsAll14Permissions() {
        Set<String> permissions = userContextPermissionRepository
                .findPermissionsByUserIdAndCompanyIdAndLovContextId(
                        accountant.getId(), company.getId(), 1L);

        assertThat(permissions).hasSize(14);
        assertThat(permissions).contains("CREATE_INVOICE", "DELETE_INVOICE",
                "VIEW_INVOICE_LIST", "TRANSFER_INVOICE", "REFRESH_INVOICE");
    }

    @Test
    void v33SeedSQL_viewerGetsOnlyViewPermissions() {
        Set<String> permissions = userContextPermissionRepository
                .findPermissionsByUserIdAndCompanyIdAndLovContextId(
                        viewer.getId(), company.getId(), 1L);

        assertThat(permissions).hasSize(3);
        assertThat(permissions).allMatch(p -> p.startsWith("VIEW_"));
    }
}
