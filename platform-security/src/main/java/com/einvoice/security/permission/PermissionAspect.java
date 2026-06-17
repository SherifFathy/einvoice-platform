package com.einvoice.security.permission;

import com.einvoice.core.error.CompanyContextRequiredException;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.security.tenant.TenantContext;
import java.lang.reflect.Parameter;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

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

        // In a company-less (AUTHORITY_SCOPED) session, the operative company for a
        // write is supplied in the request path. Resolve it once and stamp it into
        // the tenant context so downstream loads enforce that the targeted document
        // actually belongs to this company (prevents cross-company writes).
        if (ctx.mode() == TenantContext.Mode.AUTHORITY_SCOPED && ctx.companyId() == null) {
            UUID pathCompanyId = extractPathCompanyId(joinPoint);
            if (pathCompanyId != null) {
                ctx = withCompany(ctx, pathCompanyId);
                TenantContext.set(ctx);
            }
        }

        if (ctx.isSuperUser() && (ctx.mode() == TenantContext.Mode.OPERATIONAL_MODE
                || ctx.mode() == TenantContext.Mode.AUTHORITY_SCOPED)) {
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

    private TenantContext.Holder withCompany(TenantContext.Holder ctx, UUID companyId) {
        return new TenantContext.Holder(
                ctx.userId(), companyId, ctx.authorityEnvironmentId(),
                ctx.authority(), ctx.environment(), ctx.mode(),
                ctx.isSuperUser(), ctx.issuedAt(), ctx.jti());
    }

    private UUID extractPathCompanyId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] params = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        // Prefer the path variable explicitly named "companyId".
        for (int i = 0; i < params.length && i < args.length; i++) {
            PathVariable pv = params[i].getAnnotation(PathVariable.class);
            if (pv != null && args[i] instanceof UUID uuid) {
                String name = !pv.value().isEmpty() ? pv.value()
                        : (!pv.name().isEmpty() ? pv.name() : params[i].getName());
                if ("companyId".equals(name)) {
                    return uuid;
                }
            }
        }
        // Fallback: first UUID argument (companyId is the leading path variable on
        // every operational controller, so this matches even without -parameters).
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }
}
