package com.einvoice.security.permission;

import com.einvoice.core.domain.enums.Permission;
import com.einvoice.core.security.RequiresPermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** AOP aspect that enforces fine-grained permission checks on annotated methods. */
@Aspect
@Component
public class PermissionAspect {

    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Intercepts methods annotated with {@link RequiresPermission} and checks access.
     *
     * @param joinPoint the intercepted join point
     * @return the result of proceeding with the method execution
     * @throws Throwable if the underlying method or permission check fails
     */
    @Around("@annotation(com.einvoice.core.security.RequiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequiresPermission annotation = signature.getMethod()
                .getAnnotation(RequiresPermission.class);
        Permission required = annotation.value();

        var permissions = permissionService.getCurrentPermissions();
        if (!permissions.contains(required)) {
            throw new AccessDeniedException(
                    "Missing permission: " + required.name());
        }

        return joinPoint.proceed();
    }
}
