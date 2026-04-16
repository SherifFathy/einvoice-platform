package com.einvoice.core.service;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.AuditLog;
import com.einvoice.core.repository.AuditLogRepository;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Append-only audit service for recording administrative actions. */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an audit log entry in a separate transaction so that
     * a rollback of the caller transaction does not lose the audit record.
     *
     * @param action the action being performed
     * @param entityType the type of entity
     * @param entityId the entity identifier
     * @param payloadBefore JSON string of the entity state before the action
     * @param payloadAfter JSON string of the entity state after the action
     * @param companyId explicit company ID (used when TenantContext is unavailable, e.g. admin ops)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId,
            String payloadBefore, String payloadAfter, Long companyId) {
        Long resolvedCompanyId = companyId != null ? companyId : TenantContext.getCurrentTenantId();
        if (resolvedCompanyId == null) {
            resolvedCompanyId = 0L;
        }
        AuditLog entry = AuditLog.builder()
                .companyId(resolvedCompanyId)
                .userId(getCurrentUserId())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .payloadBefore(payloadBefore)
                .payloadAfter(payloadAfter)
                .ipAddress(getClientIp())
                .timestamp(OffsetDateTime.now())
                .build();
        auditLogRepository.save(entry);
    }

    /**
     * Queries audit logs for a company with optional filters.
     * @param companyId the company identifier
     * @param pageable pagination information
     * @return a page of audit log entries
     */
    public Page<AuditLog> findByCompany(Long companyId, Pageable pageable) {
        return auditLogRepository.findByCompanyId(companyId, pageable);
    }

    /**
     * Queries audit logs filtered by entity type.
     * @param companyId the company identifier
     * @param entityType the entity type filter
     * @param pageable pagination information
     * @return a page of audit log entries
     */
    public Page<AuditLog> findByCompanyAndEntityType(Long companyId,
            String entityType, Pageable pageable) {
        return auditLogRepository.findByCompanyIdAndEntityType(companyId, entityType, pageable);
    }

    /**
     * Queries audit logs filtered by entity type and ID.
     * @param companyId the company identifier
     * @param entityType the entity type filter
     * @param entityId the entity identifier
     * @param pageable pagination information
     * @return a page of audit log entries
     */
    public Page<AuditLog> findByCompanyAndEntity(Long companyId,
            String entityType, String entityId, Pageable pageable) {
        return auditLogRepository.findByCompanyIdAndEntityTypeAndEntityId(
                companyId, entityType, entityId, pageable);
    }

    /**
     * Queries audit logs filtered by date range.
     * @param companyId the company identifier
     * @param from the start of the date range
     * @param to the end of the date range
     * @param pageable pagination information
     * @return a page of audit log entries
     */
    public Page<AuditLog> findByCompanyAndDateRange(Long companyId,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        return auditLogRepository.findByCompanyIdAndTimestampBetween(
                companyId, from, to, pageable);
    }

    /**
     * Queries audit logs with combined filters. Null parameters are ignored.
     * @param companyId the company identifier (required)
     * @param entityType optional entity type filter
     * @param entityId optional entity identifier filter
     * @param from optional start of date range
     * @param to optional end of date range
     * @param pageable pagination information
     * @return a page of audit log entries
     */
    public Page<AuditLog> findFiltered(Long companyId, String entityType,
            String entityId, OffsetDateTime from, OffsetDateTime to,
            Pageable pageable) {
        boolean hasEntityType = entityType != null && !entityType.isBlank();
        boolean hasEntityId = entityId != null && !entityId.isBlank();
        boolean hasDateRange = from != null && to != null;

        if (hasEntityType && hasEntityId && hasDateRange) {
            return auditLogRepository.findByCompanyIdAndEntityTypeAndEntityIdAndTimestampBetween(
                    companyId, entityType, entityId, from, to, pageable);
        }
        if (hasEntityType && hasEntityId) {
            return auditLogRepository.findByCompanyIdAndEntityTypeAndEntityId(
                    companyId, entityType, entityId, pageable);
        }
        if (hasEntityType && hasDateRange) {
            return auditLogRepository.findByCompanyIdAndEntityTypeAndTimestampBetween(
                    companyId, entityType, from, to, pageable);
        }
        if (hasEntityType) {
            return auditLogRepository.findByCompanyIdAndEntityType(
                    companyId, entityType, pageable);
        }
        if (hasDateRange) {
            return auditLogRepository.findByCompanyIdAndTimestampBetween(
                    companyId, from, to, pageable);
        }
        return auditLogRepository.findByCompanyId(companyId, pageable);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return -1L;
    }

    private String getClientIp() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return "unknown";
    }

    /** Runtime exception for audit logging failures. */
    public static class AuditException extends RuntimeException {
        AuditException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
