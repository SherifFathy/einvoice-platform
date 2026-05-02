package com.einvoice.security.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.Permission;
import com.einvoice.core.repository.UserContextPermissionRepository;
import com.einvoice.core.repository.UserRepository;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private UserContextPermissionRepository userContextPermissionRepository;

    @Mock
    private UserRepository userRepository;

    private PermissionCache permissionCache;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionCache = new PermissionCache();
        permissionService = new PermissionService(
                userContextPermissionRepository, userRepository, permissionCache);
    }

    @Test
    void getPermissions_superUser_returnsAll14Permissions() {
        User superUser = User.builder()
                .id(1L).email("admin@test.com").name("Admin")
                .passwordHash("x").isSuperUser(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(superUser));

        Set<Permission> result = permissionService.getPermissions(1L, 10L, 1L);

        assertEquals(14, result.size());
        assertEquals(EnumSet.allOf(Permission.class), result);
    }

    @Test
    void getPermissions_nonSuperUserWith3Permissions_returnsExactlyThose3() {
        User user = User.builder()
                .id(2L).email("user@test.com").name("User")
                .passwordHash("x").isSuperUser(false).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userContextPermissionRepository.findPermissionsByUserIdAndCompanyIdAndLovContextId(
                2L, 10L, 1L))
                .thenReturn(Set.of("VIEW_INVOICE_LIST", "VIEW_CUSTOMER_LIST", "VIEW_ITEM_LIST"));

        Set<Permission> result = permissionService.getPermissions(2L, 10L, 1L);

        assertEquals(3, result.size());
        assertTrue(result.contains(Permission.VIEW_INVOICE_LIST));
        assertTrue(result.contains(Permission.VIEW_CUSTOMER_LIST));
        assertTrue(result.contains(Permission.VIEW_ITEM_LIST));
    }

    @Test
    void getPermissions_nonSuperUserWithNoPermissions_returnsEmptySet() {
        User user = User.builder()
                .id(3L).email("noperm@test.com").name("NoPerm")
                .passwordHash("x").isSuperUser(false).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userContextPermissionRepository.findPermissionsByUserIdAndCompanyIdAndLovContextId(
                3L, 10L, 1L))
                .thenReturn(Set.of());

        Set<Permission> result = permissionService.getPermissions(3L, 10L, 1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getPermissions_nullRepositoryResult_returnsEmptySet() {
        User user = User.builder()
                .id(4L).email("null@test.com").name("Null")
                .passwordHash("x").isSuperUser(false).build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userContextPermissionRepository.findPermissionsByUserIdAndCompanyIdAndLovContextId(
                4L, 10L, 1L))
                .thenReturn(null);

        Set<Permission> result = permissionService.getPermissions(4L, 10L, 1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getPermissions_secondCall_hitsCacheNotRepository() {
        User user = User.builder().id(5L).email("cache@test.com").name("Cache")
                .passwordHash("x").isSuperUser(false).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userContextPermissionRepository.findPermissionsByUserIdAndCompanyIdAndLovContextId(5L, 10L, 1L))
                .thenReturn(Set.of("VIEW_INVOICE_LIST"));

        permissionService.getPermissions(5L, 10L, 1L);
        permissionService.getPermissions(5L, 10L, 1L);

        verify(userContextPermissionRepository, times(1))
                .findPermissionsByUserIdAndCompanyIdAndLovContextId(5L, 10L, 1L);
        verify(userRepository, times(1)).findById(5L);
    }
}
