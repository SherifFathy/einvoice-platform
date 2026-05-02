package com.einvoice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.core.context.LovContextMappingProvider;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.LovContext;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.enums.Role;
import com.einvoice.core.exception.InvalidCredentialsException;
import com.einvoice.core.repository.LovContextRepository;
import com.einvoice.core.repository.RefreshTokenRepository;
import com.einvoice.core.repository.UserCompanyRoleRepository;
import com.einvoice.core.repository.UserContextPermissionRepository;
import com.einvoice.core.repository.UserEnvironmentPermissionRepository;
import com.einvoice.core.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCompanyRoleRepository userCompanyRoleRepository;
    @Mock private UserEnvironmentPermissionRepository userEnvironmentPermissionRepository;
    @Mock private UserContextPermissionRepository userContextPermissionRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LovContextRepository lovContextRepository;
    @Mock private LovContextMappingProvider lovContextMappingProvider;
    @Mock private TokenService tokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    private AuthenticationService authService;

    private User activeUser;
    private Company activeCompany;
    private UserCompanyRole primaryRole;
    private LovContext zatcaSandboxContext;

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService(
                userRepository, userCompanyRoleRepository,
                userEnvironmentPermissionRepository, userContextPermissionRepository,
                refreshTokenRepository, lovContextRepository,
                lovContextMappingProvider, tokenService,
                passwordEncoder, auditService);

        activeCompany = Company.builder()
                .id(1L).nameEn("Test Co").isActive(true).build();

        activeUser = User.builder()
                .id(42L).name("Test User").email("test@example.com")
                .passwordHash("hashed").isActive(true).isSuperUser(false).build();

        primaryRole = UserCompanyRole.builder()
                .id(1L).user(activeUser).company(activeCompany)
                .role(Role.COMPANY_ADMIN).isActive(true).build();

        zatcaSandboxContext = LovContext.builder()
                .id(1L).authority("ZATCA").docType("INVOICE")
                .subEnv("SANDBOX").contextKey("ZATCA-INVOICE-SANDBOX")
                .createdAt(OffsetDateTime.now()).build();

        when(tokenService.generateAccessToken(anyLong(), anyString(), anyString(),
                anyLong(), anyString(), any(), any(), anyString(),
                anyString(), anyString(), anyString(),
                anyLong(), any(), any(Boolean.class)))
                .thenReturn("access-token");
        when(tokenService.generateRefreshToken(anyLong(), anyLong(), anyLong()))
                .thenReturn("refresh-token");
        when(tokenService.getAccessTokenExpirySeconds()).thenReturn(900L);
        when(tokenService.getRefreshTokenExpirySeconds()).thenReturn(604800L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("login with LOV context")
    class LoginWithLovContext {

        @Test
        void login_validLovFields_returnsAuthResultWithLovClaims() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
            when(userCompanyRoleRepository.findByUserIdAndIsActiveTrue(42L))
                    .thenReturn(List.of(primaryRole));
            when(lovContextRepository.findByContextKey("ZATCA-INVOICE-SANDBOX"))
                    .thenReturn(Optional.of(zatcaSandboxContext));
            when(lovContextMappingProvider.toAuthorityEnvironment("ZATCA", "SANDBOX"))
                    .thenReturn("ZATCA_SANDBOX");
            when(userContextPermissionRepository.findPermissionsByUserIdAndCompanyIdAndLovContextId(
                    42L, 1L, 1L)).thenReturn(Set.of("CREATE_INVOICE", "VIEW_INVOICE_LIST"));

            AuthenticationService.AuthResult result = authService.login(
                    "test@example.com", "password",
                    "ZATCA", "INVOICE", "SANDBOX");

            assertNotNull(result);
            assertEquals("ZATCA", result.activeAuthority);
            assertEquals("INVOICE", result.activeDocType);
            assertEquals("SANDBOX", result.activeSubEnv);
            assertEquals(1L, result.lovContextId);
            assertFalse(result.isSuperUser);
            assertTrue(result.permissions.contains("CREATE_INVOICE"));
            assertTrue(result.permissions.contains("VIEW_INVOICE_LIST"));
            assertEquals(42L, result.user.getId());
            assertEquals(1L, result.activeCompanyId);
            assertEquals("COMPANY_ADMIN", result.role);
        }

        @Test
        void login_invalidLovCombo_throws400() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
            when(userCompanyRoleRepository.findByUserIdAndIsActiveTrue(42L))
                    .thenReturn(List.of(primaryRole));
            when(lovContextRepository.findByContextKey("ZATCA-INVOICE-PREPROD"))
                    .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    authService.login("test@example.com", "password",
                            "ZATCA", "INVOICE", "PREPROD"));
            assertTrue(ex.getMessage().contains("Invalid LOV context combination"));
        }

        @Test
        void login_invalidAuthoritySubEnvMapperThrows_throws400() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
            when(userCompanyRoleRepository.findByUserIdAndIsActiveTrue(42L))
                    .thenReturn(List.of(primaryRole));
            LovContext fakeContext = LovContext.builder()
                    .id(99L).authority("ZATCA").docType("INVOICE")
                    .subEnv("PREPROD").contextKey("ZATCA-INVOICE-PREPROD")
                    .createdAt(OffsetDateTime.now()).build();
            when(lovContextRepository.findByContextKey("ZATCA-INVOICE-PREPROD"))
                    .thenReturn(Optional.of(fakeContext));
            when(lovContextMappingProvider.toAuthorityEnvironment("ZATCA", "PREPROD"))
                    .thenThrow(new IllegalArgumentException(
                            "Unknown subEnv for ZATCA: PREPROD"));

            assertThrows(IllegalArgumentException.class, () ->
                    authService.login("test@example.com", "password",
                            "ZATCA", "INVOICE", "PREPROD"));
        }

        @Test
        void login_superUser_isSuperUserTrueInResult() {
            User superUser = User.builder()
                    .id(1L).name("Admin").email("admin@example.com")
                    .passwordHash("hashed").isActive(true).isSuperUser(true).build();
            UserCompanyRole superRole = UserCompanyRole.builder()
                    .id(10L).user(superUser).company(activeCompany)
                    .role(Role.SUPER_USER).isActive(true).build();

            when(userRepository.findByEmail("admin@example.com"))
                    .thenReturn(Optional.of(superUser));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
            when(userCompanyRoleRepository.findByUserIdAndIsActiveTrue(1L))
                    .thenReturn(List.of(superRole));
            when(lovContextRepository.findByContextKey("ZATCA-INVOICE-SANDBOX"))
                    .thenReturn(Optional.of(zatcaSandboxContext));
            when(lovContextMappingProvider.toAuthorityEnvironment("ZATCA", "SANDBOX"))
                    .thenReturn("ZATCA_SANDBOX");
            when(userContextPermissionRepository.findPermissionsByUserIdAndCompanyIdAndLovContextId(
                    1L, 1L, 1L)).thenReturn(Set.of());

            AuthenticationService.AuthResult result = authService.login(
                    "admin@example.com", "password",
                    "ZATCA", "INVOICE", "SANDBOX");

            assertTrue(result.isSuperUser);
        }

        @Test
        void login_invalidCredentials_throws() {
            when(userRepository.findByEmail("bad@example.com"))
                    .thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class, () ->
                    authService.login("bad@example.com", "wrong",
                            "ZATCA", "INVOICE", "SANDBOX"));
        }

        @Test
        void login_deactivatedUser_throws() {
            User inactiveUser = User.builder()
                    .id(2L).email("inactive@test.com").isActive(false)
                    .passwordHash("h").build();
            when(userRepository.findByEmail("inactive@test.com"))
                    .thenReturn(Optional.of(inactiveUser));

            assertThrows(InvalidCredentialsException.class, () ->
                    authService.login("inactive@test.com", "pass",
                            "ZATCA", "INVOICE", "SANDBOX"));
        }

        @Test
        void login_mapperIsCalledWithCorrectArgs() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
            when(userCompanyRoleRepository.findByUserIdAndIsActiveTrue(42L))
                    .thenReturn(List.of(primaryRole));
            when(lovContextRepository.findByContextKey("ZATCA-INVOICE-SANDBOX"))
                    .thenReturn(Optional.of(zatcaSandboxContext));
            when(lovContextMappingProvider.toAuthorityEnvironment("ZATCA", "SANDBOX"))
                    .thenReturn("ZATCA_SANDBOX");
            when(userContextPermissionRepository.findPermissionsByUserIdAndCompanyIdAndLovContextId(
                    42L, 1L, 1L)).thenReturn(Set.of());

            authService.login("test@example.com", "password",
                    "ZATCA", "INVOICE", "SANDBOX");

            verify(lovContextMappingProvider).toAuthorityEnvironment("ZATCA", "SANDBOX");
        }
    }
}
