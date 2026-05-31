package com.einvoice.security.operational;

import com.einvoice.core.error.CompanyContextRequiredException;
import com.einvoice.security.tenant.TenantContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** AOP aspect that rejects requests in Admin Mode for operational endpoints. */
@Aspect
@Component
@Order(0)
public class OperationalModeAspect {

    /**
     * Before-advice that checks the current tenant context is in operational mode.
     *
     * @param joinPoint the join point being intercepted
     */
    @Before("@within(RequireOperationalMode) || @annotation(RequireOperationalMode)")
    public void enforceOperationalMode(JoinPoint joinPoint) {
        TenantContext.Holder ctx = TenantContext.current();
        if (ctx == null || ctx.mode() == TenantContext.Mode.ADMIN_MODE) {
            throw new CompanyContextRequiredException(
                    "A company selection is required for operational endpoints");
        }
    }
}
