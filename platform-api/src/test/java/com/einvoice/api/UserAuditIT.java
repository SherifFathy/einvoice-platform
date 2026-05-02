package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.AuditLog;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.LovContext;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.Role;
import com.einvoice.core.repository.AuditLogRepository;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.LovContextRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserRepository;
import com.einvoice.core.service.UserService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
@AutoConfigureMockMvc
@Testcontainers
class UserAuditIT {

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
    private LovContextRepository lovContextRepository;

    @Autowired
    private UserCompanyRoleRepository userCompanyRoleRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Company company;
    private User superUser;
    private LovContext lovContext;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        company = new Company();
        company.setNameAr("شركة اختبار");
        company.setNameEn("Test Company");
        company.setVatNumber("300000000000003");
        company.setCrNumber("CR003");
        company = companyRepository.save(company);

        superUser = User.builder()
                .name("Super Admin")
                .email("super-admin-audit@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .isActive(true)
                .isSuperUser(true)
                .build();
        superUser = userRepository.save(superUser);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(superUser.getId(), "credentials",
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_SUPER_USER"))));

        lovContext = lovContextRepository.findByContextKey("ZATCA-INVOICE-SANDBOX")
                .orElseThrow();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        auditLogRepository.deleteAll();
        userCompanyRoleRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void createUser_writesAuditLog() {
        User created = userService.createUser("Audit User", "audit-create@test.com", "pass123");

        List<AuditLog> logs = auditLogRepository.findByCompanyId(0L, PageRequest.of(0, 50))
                .stream()
                .filter(l -> "USER_CREATED".equals(l.getAction()))
                .toList();

        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getEntityType()).isEqualTo("User");
        assertThat(log.getEntityId()).isEqualTo(created.getId().toString());
        assertThat(log.getPayloadAfter()).contains("audit-create@test.com");
    }

    @Test
    void grantPermission_writesPermissionGrantedAuditLog() {
        User target = userService.createUser("Perm Target", "perm-target@test.com", "pass123");

        userService.bulkSetPermissions(target.getId(), company.getId(),
                lovContext.getId(),
                Set.of("VIEW_INVOICE_LIST", "VIEW_CUSTOMER_LIST"),
                superUser.getId());

        List<AuditLog> logs = auditLogRepository.findAll(PageRequest.of(0, 50))
                .stream()
                .filter(l -> "PERMISSION_GRANTED".equals(l.getAction()))
                .toList();

        assertThat(logs).hasSize(2);
        assertThat(logs).allMatch(l -> l.getEntityType().equals("UserContextPermission"));
        assertThat(logs).anyMatch(l -> l.getPayloadAfter() != null
                && l.getPayloadAfter().contains("VIEW_INVOICE_LIST")
                && l.getPayloadAfter().contains(String.valueOf(company.getId()))
                && l.getPayloadAfter().contains(String.valueOf(lovContext.getId())));
    }

    @Test
    void revokePermission_writesPermissionRevokedAuditLog() {
        User target = userService.createUser("Revoke Target", "revoke-target@test.com", "pass123");

        userService.bulkSetPermissions(target.getId(), company.getId(),
                lovContext.getId(),
                Set.of("VIEW_INVOICE_LIST", "VIEW_CUSTOMER_LIST", "VIEW_ITEM_LIST"),
                superUser.getId());

        userService.bulkSetPermissions(target.getId(), company.getId(),
                lovContext.getId(),
                Set.of("VIEW_INVOICE_LIST"),
                superUser.getId());

        List<AuditLog> revokeLogs = auditLogRepository.findAll(PageRequest.of(0, 100))
                .stream()
                .filter(l -> "PERMISSION_REVOKED".equals(l.getAction()))
                .toList();

        assertThat(revokeLogs).hasSize(2);
        assertThat(revokeLogs).anyMatch(l -> l.getPayloadAfter() != null
                && l.getPayloadAfter().contains("VIEW_CUSTOMER_LIST"));
        assertThat(revokeLogs).anyMatch(l -> l.getPayloadAfter() != null
                && l.getPayloadAfter().contains("VIEW_ITEM_LIST"));
    }

    @Test
    void deactivateUser_writesAuditLog() {
        User target = userService.createUser("Deactivate Target", "deactivate-target@test.com", "pass123");

        userService.deactivateUser(superUser.getId(), target.getId());

        List<AuditLog> logs = auditLogRepository.findAll(PageRequest.of(0, 50))
                .stream()
                .filter(l -> "USER_DEACTIVATED".equals(l.getAction()))
                .toList();

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEntityId()).isEqualTo(target.getId().toString());
    }
}
