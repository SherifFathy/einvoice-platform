package com.einvoice.api.audit.service;

import com.einvoice.core.domain.shared.AuditLog;
import com.einvoice.core.repository.shared.AuditLogRepository;
import com.einvoice.security.tenant.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Append-only audit log writer for all platform actions. */
@Service
@Transactional
public class AuditService {

    private final AuditLogRepository repository;

    /**
     * Constructs an AuditService.
     *
     * @param repository the audit log repository
     */
    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an audit log entry.
     *
     * @param action the action being performed
     * @param entityType the type of entity
     * @param entityId the entity identifier
     * @param payloadBefore the entity state before the action
     * @param payloadAfter the entity state after the action
     */
    public void record(String action, String entityType, String entityId,
            Map<String, Object> payloadBefore,
            Map<String, Object> payloadAfter) {
        record(null, action, entityType, entityId, payloadBefore, payloadAfter);
    }

    /**
     * Records an audit log entry with an explicit company ID.
     *
     * @param companyId the explicit company ID (may be null for cross-company reads)
     * @param action the action being performed
     * @param entityType the type of entity
     * @param entityId the entity identifier
     * @param payloadBefore the entity state before the action
     * @param payloadAfter the entity state after the action
     */
    public void record(UUID companyId, String action, String entityType,
            String entityId,
            Map<String, Object> payloadBefore,
            Map<String, Object> payloadAfter) {
        UUID resolvedCompanyId = companyId != null
                ? companyId : TenantContext.getCompanyId();
        AuditLog log = AuditLog.builder()
                .companyId(resolvedCompanyId)
                .authorityEnvironmentId(
                        TenantContext.getAuthorityEnvironmentId())
                .userId(TenantContext.getUserId())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .payloadBefore(payloadBefore)
                .payloadAfter(payloadAfter)
                .build();
        repository.save(log);
    }
}
