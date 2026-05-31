package com.einvoice.security.permission;

import com.einvoice.core.error.CompanyContextRequiredException;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.security.tenant.TenantContext;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/** Javadoc. */
@Aspect
@Component
public class PermissionAspect {

    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Javadoc.
     * @param joinPoint AOP join point
     * @return proceeding result
     */
    @Around("@annotation(com.einvoice.core.security.RequiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        TenantContext.Holder ctx = TenantContext.current();
        if (ctx == null) {
            throw new CompanyContextRequiredException("No tenant context");
        }

        if (ctx.isSuperUser() && ctx.mode() == TenantContext.Mode.OPERATIONAL_MODE) {
            return joinPoint.proceed();
        }

        if (ctx.mode() == TenantContext.Mode.ADMIN_MODE) {
            throw new CompanyContextRequiredException(
                    "Admin Mode cannot access operational endpoints");
        }

        RequiresPermission annotation = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getAnnotation(RequiresPermission.class);

        String transactionType = annotation.transactionType();
        String action = annotation.action();

        UUID companyId = ctx.companyId();
        if (companyId == null) {
            throw new CompanyContextRequiredException(
                    "A company selection is required for operational endpoints");
        }

        boolean hasPermission = permissionService.hasPermission(
                ctx.userId(), companyId, ctx.authorityEnvironmentId(),
                transactionType, action);

        if (!hasPermission) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Missing permission: " + transactionType + ":" + action);
        }

        return joinPoint.proceed();
    }
}
