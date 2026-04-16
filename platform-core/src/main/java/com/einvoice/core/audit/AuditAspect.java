package com.einvoice.core.audit;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that captures entity state before and after method execution
 * and delegates audit persistence to {@link AuditService} which runs in
 * a separate transaction ({@code REQUIRES_NEW}).
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;
    private final EntityManager entityManager;
    private final ObjectMapper auditObjectMapper;

    /**
     * Creates the audit aspect.
     *
     * @param auditService the audit service for persisting log entries
     * @param entityManager the JPA entity manager for loading before-state
     */
    public AuditAspect(AuditService auditService, EntityManager entityManager) {
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.auditObjectMapper = new ObjectMapper();
        this.auditObjectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.auditObjectMapper.registerModule(new JavaTimeModule());
        this.auditObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Around advice that captures entity state before and after method execution
     * and delegates audit persistence to {@link AuditService}.
     *
     * @param joinPoint the join point
     * @return the result of the method execution
     * @throws Throwable if the method throws
     */
    @Around("@annotation(com.einvoice.core.audit.Audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Audited audited = method.getAnnotation(Audited.class);

        String payloadBefore = resolveBeforeState(joinPoint);

        Object result = joinPoint.proceed();

        String payloadAfter = serialize(result);
        String entityId = extractEntityId(result, joinPoint.getArgs());
        Long companyId = resolveCompanyId(result, joinPoint.getArgs());

        auditService.log(
                audited.action(),
                audited.entityType(),
                entityId,
                payloadBefore,
                payloadAfter,
                companyId);

        return result;
    }

    /**
     * Resolves the company ID by trying multiple strategies:
     * 1. TenantContext (set by TenantFilter for non-admin requests)
     * 2. Result object's getCompany().getId() or getCompanyId()
     * 3. Any method argument's getCompany().getId() or getCompanyId()
     * 4. If a Company entity is the result, use its getId()
     * 5. Fallback: null (AuditService will default to 0L)
     */
    private Long resolveCompanyId(Object result, Object[] args) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }

        // Try extracting from result
        Long fromResult = extractCompanyId(result);
        if (fromResult != null) {
            return fromResult;
        }

        // Check if result IS a Company (for CompanyService.create/activate/deactivate)
        if (result != null) {
            Long resultId = extractIdIfCompany(result);
            if (resultId != null) {
                return resultId;
            }
        }

        // Try ALL method arguments, not just the first
        if (args != null) {
            for (Object arg : args) {
                Long fromArg = extractCompanyId(arg);
                if (fromArg != null) {
                    return fromArg;
                }
            }
        }

        return null;
    }

    private Long extractCompanyId(Object obj) {
        if (obj == null) {
            return null;
        }
        // Try obj.getCompany().getId()
        try {
            Method getCompany = obj.getClass().getMethod("getCompany");
            Object company = getCompany.invoke(obj);
            if (company != null) {
                Method getId = company.getClass().getMethod("getId");
                Object id = getId.invoke(company);
                if (id instanceof Long) {
                    return (Long) id;
                }
            }
        } catch (Exception ignored) {
        }
        // Try obj.getCompanyId()
        try {
            Method getCompanyId = obj.getClass().getMethod("getCompanyId");
            Object id = getCompanyId.invoke(obj);
            if (id instanceof Long) {
                return (Long) id;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * If the object is a Company entity, return its ID directly.
     */
    private Long extractIdIfCompany(Object obj) {
        try {
            if (obj.getClass().getSimpleName().equals("Company")) {
                Method getId = obj.getClass().getMethod("getId");
                Object id = getId.invoke(obj);
                if (id instanceof Long) {
                    return (Long) id;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String resolveBeforeState(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            Audited audited = sig.getMethod().getAnnotation(Audited.class);
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return null;
            }

            Object firstArg = args[0];
            Class<?> entityClass = audited.entityClass();
            Object entityId;

            if (entityClass != void.class) {
                if (firstArg instanceof Number) {
                    entityId = ((Number) firstArg).longValue();
                } else {
                    entityId = firstArg;
                }
            } else {
                Method getIdMethod;
                try {
                    getIdMethod = firstArg.getClass().getMethod("getId");
                } catch (NoSuchMethodException ex) {
                    return null;
                }
                entityId = getIdMethod.invoke(firstArg);
                entityClass = firstArg.getClass();
            }

            if (entityId == null) {
                return null;
            }
            Object dbEntity = entityManager.find(entityClass, entityId);
            if (dbEntity == null) {
                return null;
            }
            return serialize(dbEntity);
        } catch (Exception e) {
            return null;
        }
    }

    private String serialize(Object obj) {
        try {
            if (obj == null) {
                return null;
            }
            return auditObjectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private String extractEntityId(Object result, Object[] args) {
        try {
            if (result != null) {
                Method getIdMethod = result.getClass().getMethod("getId");
                Object id = getIdMethod.invoke(result);
                return id != null ? id.toString() : "unknown";
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }
}
