package com.einvoice.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
@SuppressWarnings("java:S5778")
class PermissionParityIT {

    // T096 (a): verifies GET /api/session/context permission flags match V42 seed
    // T096 (b): guarded-endpoint parity (200 when granted, 403 when not) is deferred
    // to Wave 6+ alongside the first operational endpoints (per spec.md Clarification I1).

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

    private static final Set<String> DOC_ACTIONS = Set.of(
            "VIEW", "CREATE", "EDIT", "DELETE", "CANCEL", "TRANSFER", "REFRESH", "SUBMIT");
    private static final Set<String> MD_ACTIONS = Set.of(
            "VIEW", "CREATE", "EDIT", "DELETE", "REFRESH");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserCompanyTransactionRoleRepository uctrRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    private Company company;
    private User superUser;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .nameEn("Parity Co")
                .nameAr("شركة التكافؤ")
                .taxNumber("999000000000001")
                .isActive(true)
                .build();
        company = companyRepository.save(company);

        user = User.builder()
                .name("Parity User")
                .email("parity@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(false)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        superUser = User.builder()
                .name("Parity SU")
                .email("parity-su@test.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        superUser = userRepository.save(superUser);

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(superUser).company(company)
                .authorityEnvironmentId((short) 2)
                .transactionType("INVOICE").roleCode("COMPANY_ADMIN")
                .isActive(true).build());
    }

    @AfterEach
    void tearDown() {
        uctrRepo.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    static Stream<Arguments> documentRoles() {
        return Stream.of(
                Arguments.of("ETA", "PREPROD", (short) 2, "INVOICE", "COMPANY_ADMIN"),
                Arguments.of("ETA", "PREPROD", (short) 2, "INVOICE", "ACCOUNTANT"),
                Arguments.of("ETA", "PREPROD", (short) 2, "INVOICE", "VIEWER"),
                Arguments.of("ETA", "PREPROD", (short) 2, "RECEIPT", "COMPANY_ADMIN"),
                Arguments.of("ETA", "PREPROD", (short) 2, "RECEIPT", "ACCOUNTANT"),
                Arguments.of("ETA", "PREPROD", (short) 2, "RECEIPT", "VIEWER"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "STANDARD", "COMPANY_ADMIN"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "STANDARD", "ACCOUNTANT"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "STANDARD", "VIEWER"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "SIMPLIFIED", "COMPANY_ADMIN"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "SIMPLIFIED", "ACCOUNTANT"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "SIMPLIFIED", "VIEWER")
        );
    }

    static Stream<Arguments> masterDataRoles() {
        return Stream.of(
                Arguments.of("ETA", "PREPROD", (short) 2, "CUSTOMERS", "COMPANY_ADMIN"),
                Arguments.of("ETA", "PREPROD", (short) 2, "CUSTOMERS", "ACCOUNTANT"),
                Arguments.of("ETA", "PREPROD", (short) 2, "ITEMS", "COMPANY_ADMIN"),
                Arguments.of("ETA", "PREPROD", (short) 2, "ITEMS", "ACCOUNTANT"),
                Arguments.of("ETA", "PREPROD", (short) 2, "CONFIG", "COMPANY_ADMIN"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "CUSTOMERS", "COMPANY_ADMIN"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "CUSTOMERS", "ACCOUNTANT"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "ITEMS", "COMPANY_ADMIN"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "ITEMS", "ACCOUNTANT"),
                Arguments.of("ZATCA", "SANDBOX", (short) 5, "CONFIG", "COMPANY_ADMIN")
        );
    }

    @ParameterizedTest(name = "{0} {3} {4}")
    @MethodSource("documentRoles")
    void documentPermissionParity_sessionContext_matchesSeedPermissions(
            String authority, String environment, short authEnvId,
            String transactionType, String roleCode) throws Exception {

        Set<String> expected = expectedDocumentPermissions(roleCode);
        verifyPermissionParity(authority, environment, authEnvId, transactionType, roleCode, expected);
    }

    @ParameterizedTest(name = "{0} {3} {4}")
    @MethodSource("masterDataRoles")
    void masterDataPermissionParity_sessionContext_matchesSeedPermissions(
            String authority, String environment, short authEnvId,
            String transactionType, String roleCode) throws Exception {

        Set<String> expected = expectedMasterDataPermissions(roleCode);
        verifyPermissionParity(authority, environment, authEnvId, transactionType, roleCode, expected);
    }

    @Test
    void superUserOperationalMode_allPermissionsTrue() throws Exception {
        String token = jwtTokenProvider.createToken(
                superUser.getId(), superUser.getEmail(), true,
                "ETA", "PREPROD", (short) 2,
                company.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        MvcResult result = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ctx = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode companies = ctx.get("companies");
        assertThat(companies.size()).isGreaterThanOrEqualTo(1);

        for (JsonNode c : companies) {
            JsonNode modules = c.get("modules");
            for (String moduleKey : List.of("invoice", "receipt", "customers", "items", "configuration")) {
                JsonNode mod = modules.get(moduleKey);
                assertThat(mod).isNotNull();
                assertThat(mod.get("visible").asBoolean()).isTrue();
                JsonNode perms = mod.get("permissions");
                assertThat(perms.get("view").asBoolean()).isTrue();
                assertThat(perms.get("create").asBoolean()).isTrue();
                assertThat(perms.get("edit").asBoolean()).isTrue();
                assertThat(perms.get("delete").asBoolean()).isTrue();
                assertThat(perms.get("refresh").asBoolean()).isTrue();
            }
        }
    }

    private void verifyPermissionParity(String authority, String environment, short authEnvId,
            String transactionType, String roleCode, Set<String> expected) throws Exception {

        uctrRepo.save(UserCompanyTransactionRole.builder()
                .user(user).company(company)
                .authorityEnvironmentId(authEnvId)
                .transactionType(transactionType).roleCode(roleCode)
                .isActive(true).build());

        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), false,
                authority, environment, authEnvId,
                company.getId(), TenantContext.Mode.OPERATIONAL_MODE);

        MvcResult result = mockMvc.perform(get("/api/session/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ctx = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode companies = ctx.get("companies");
        assertThat(companies.size()).isEqualTo(1);

        JsonNode companyNode = companies.get(0);
        String moduleKey = toModuleKey(authority, transactionType);
        JsonNode mod = companyNode.get("modules").get(moduleKey);
        assertThat(mod).as("module '%s' should exist", moduleKey).isNotNull();

        boolean expectVisible = expected.contains("VIEW");
        assertThat(mod.get("visible").asBoolean()).isEqualTo(expectVisible);

        JsonNode perms = mod.get("permissions");
        assertPermission(perms, "view", expected.contains("VIEW"));
        assertPermission(perms, "create", expected.contains("CREATE"));
        assertPermission(perms, "edit", expected.contains("EDIT"));
        assertPermission(perms, "delete", expected.contains("DELETE"));
        assertPermission(perms, "refresh", expected.contains("REFRESH"));

        if (isDocumentModule(moduleKey)) {
            assertPermission(perms, "cancel", expected.contains("CANCEL"));
            assertPermission(perms, "transfer", expected.contains("TRANSFER"));
            assertPermission(perms, "submit", expected.contains("SUBMIT"));
        }
    }

    private void assertPermission(JsonNode perms, String field, boolean expected) {
        assertThat(perms.get(field).asBoolean())
                .as("permissions.%s should be %s", field, expected)
                .isEqualTo(expected);
    }

    private static Set<String> expectedDocumentPermissions(String roleCode) {
        return switch (roleCode) {
          case "COMPANY_ADMIN" -> DOC_ACTIONS;
          case "ACCOUNTANT" -> Set.of("VIEW", "CREATE", "REFRESH", "SUBMIT");
          case "VIEWER" -> Set.of("VIEW");
          default -> Set.of();
        };
    }

    private static Set<String> expectedMasterDataPermissions(String roleCode) {
        return switch (roleCode) {
          case "COMPANY_ADMIN" -> MD_ACTIONS;
          case "ACCOUNTANT" -> Set.of("VIEW", "CREATE", "REFRESH");
          default -> Set.of();
        };
    }

    private static String toModuleKey(String authority, String txType) {
        return switch (txType) {
          case "INVOICE" -> "invoice";
          case "RECEIPT" -> "receipt";
          case "STANDARD" -> "standard";
          case "SIMPLIFIED" -> "simplified";
          case "CUSTOMERS" -> "customers";
          case "ITEMS" -> "items";
          case "CONFIG" -> "configuration";
          default -> txType.toLowerCase();
        };
    }

    private static boolean isDocumentModule(String moduleKey) {
        return Set.of("invoice", "receipt", "standard", "simplified").contains(moduleKey);
    }
}
