package com.einvoice.core.repository.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationalRepositorySupportTest {

    private Root<Object> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path<Object> companyIdPath;
    private Path<Object> authEnvIdPath;
    private Path<Object> idPath;
    private Supplier<OperationalRepositorySupport.TenantValues> savedSupplier;

    @BeforeEach
    void setUp() {
        savedSupplier = captureCurrentSupplier();
        OperationalRepositorySupport.setTenantValuesSupplier(null);

        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        companyIdPath = mock(Path.class);
        authEnvIdPath = mock(Path.class);
        idPath = mock(Path.class);
    }

    @AfterEach
    void tearDown() {
        OperationalRepositorySupport.setTenantValuesSupplier(savedSupplier);
    }

    @Test
    void companyIdEquals_shouldProduceEqualPredicate() {
        UUID companyId = UUID.randomUUID();
        Predicate expectedPred = mock(Predicate.class);
        when(root.get("companyId")).thenReturn(companyIdPath);
        when(cb.equal(companyIdPath, companyId)).thenReturn(expectedPred);

        var spec = OperationalRepositorySupport.<Object>companyIdEquals(companyId);
        Predicate result = spec.toPredicate(root, query, cb);

        assertEquals(expectedPred, result);
        verify(cb).equal(companyIdPath, companyId);
    }

    @Test
    void authorityEnvironmentIdEquals_shouldProduceEqualPredicate() {
        Short authEnvId = 1;
        Predicate expectedPred = mock(Predicate.class);
        when(root.get("authorityEnvironmentId")).thenReturn(authEnvIdPath);
        when(cb.equal(authEnvIdPath, authEnvId)).thenReturn(expectedPred);

        var spec = OperationalRepositorySupport.<Object>authorityEnvironmentIdEquals(authEnvId);
        Predicate result = spec.toPredicate(root, query, cb);

        assertEquals(expectedPred, result);
        verify(cb).equal(authEnvIdPath, authEnvId);
    }

    @Test
    void idEquals_shouldProduceEqualPredicate() {
        UUID id = UUID.randomUUID();
        Predicate expectedPred = mock(Predicate.class);
        when(root.get("id")).thenReturn(idPath);
        when(cb.equal(idPath, id)).thenReturn(expectedPred);

        var spec = OperationalRepositorySupport.<Object>idEquals(id);
        Predicate result = spec.toPredicate(root, query, cb);

        assertEquals(expectedPred, result);
        verify(cb).equal(idPath, id);
    }

    @Test
    void inActiveTenant_shouldThrow_whenNoSupplier() {
        var spec = OperationalRepositorySupport.<Object>inActiveTenant();
        assertThrows(IllegalStateException.class,
                () -> spec.toPredicate(root, query, cb));
    }

    @Test
    void inActiveTenant_shouldProduceCompoundAndPredicate() {
        UUID companyId = UUID.randomUUID();
        Short authEnvId = 2;
        OperationalRepositorySupport.setTenantValuesSupplier(
                () -> new OperationalRepositorySupport.TenantValues(companyId, authEnvId));

        Predicate companyPred = mock(Predicate.class);
        Predicate envPred = mock(Predicate.class);
        Predicate andPred = mock(Predicate.class);

        when(root.get("companyId")).thenReturn(companyIdPath);
        when(root.get("authorityEnvironmentId")).thenReturn(authEnvIdPath);
        when(cb.equal(companyIdPath, companyId)).thenReturn(companyPred);
        when(cb.equal(authEnvIdPath, authEnvId)).thenReturn(envPred);
        when(cb.and(companyPred, envPred)).thenReturn(andPred);

        var spec = OperationalRepositorySupport.<Object>inActiveTenant();
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        verify(cb).and(companyPred, envPred);
    }

    @Test
    void inActiveTenant_withParams_shouldProduceCompoundAndPredicate() {
        UUID companyId = UUID.randomUUID();
        Short authEnvId = 2;

        Predicate companyPred = mock(Predicate.class);
        Predicate envPred = mock(Predicate.class);
        Predicate andPred = mock(Predicate.class);

        when(root.get("companyId")).thenReturn(companyIdPath);
        when(root.get("authorityEnvironmentId")).thenReturn(authEnvIdPath);
        when(cb.equal(companyIdPath, companyId)).thenReturn(companyPred);
        when(cb.equal(authEnvIdPath, authEnvId)).thenReturn(envPred);
        when(cb.and(companyPred, envPred)).thenReturn(andPred);

        var spec = OperationalRepositorySupport.<Object>inActiveTenant(companyId, authEnvId);
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
        verify(cb).and(companyPred, envPred);
    }

    @SuppressWarnings("unchecked")
    private static Supplier<OperationalRepositorySupport.TenantValues> captureCurrentSupplier() {
        try {
            var field = OperationalRepositorySupport.class.getDeclaredField("tenantValuesSupplier");
            field.setAccessible(true);
            return (Supplier<OperationalRepositorySupport.TenantValues>) field.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
