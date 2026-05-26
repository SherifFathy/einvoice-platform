package com.einvoice.security.operational;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.einvoice.core.error.CompanyContextRequiredException;
import com.einvoice.security.permission.PermissionAspect;
import com.einvoice.security.tenant.TenantContext;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

class OperationalModeAspectTest {

    private OperationalModeAspect aspect;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        aspect = new OperationalModeAspect();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void enforceOperationalMode_throws_whenAdminMode() {
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), null, null,
                null, null, TenantContext.Mode.ADMIN_MODE,
                true, System.currentTimeMillis(), "jti"));

        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(jp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(this.getClass().getDeclaredMethods()[0]);

        CompanyContextRequiredException ex = assertThrows(
                CompanyContextRequiredException.class,
                () -> aspect.enforceOperationalMode(jp));
        assertEquals("A company selection is required for operational endpoints", ex.getMessage());
    }

    @Test
    void enforceOperationalMode_throws_whenNoContext() {
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(jp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(this.getClass().getDeclaredMethods()[0]);

        assertThrows(CompanyContextRequiredException.class,
                () -> aspect.enforceOperationalMode(jp));
    }

    @Test
    void enforceOperationalMode_passes_whenOperationalMode() {
        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), UUID.randomUUID(), (short) 1,
                "ETA", "PRODUCTION", TenantContext.Mode.OPERATIONAL_MODE,
                false, System.currentTimeMillis(), "jti"));

        JoinPoint jp = mock(JoinPoint.class);
        assertDoesNotThrow(() -> aspect.enforceOperationalMode(jp));
    }

    @Test
    void operationalModeAspect_runsBeforePermissionAspect() {
        Order opOrder = OperationalModeAspect.class.getAnnotation(Order.class);
        Order permOrder = PermissionAspect.class.getAnnotation(Order.class);

        int opValue = (opOrder != null) ? opOrder.value() : Integer.MAX_VALUE;
        int permValue = (permOrder != null) ? permOrder.value() : Integer.MAX_VALUE;

        assertTrue(opValue < permValue,
                "OperationalModeAspect (@Order(" + opValue
                        + ")) must run before PermissionAspect (@Order(" + permValue
                        + ")) so Super Users see COMPANY_CONTEXT_REQUIRED, not FORBIDDEN");
    }
}
