package com.einvoice.core.service.audit;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable read-model records returned by {@link AuditLogQueryService} for the
 * authority-scoped audit-log viewer. These live in {@code platform-core} so the
 * query service can return them without depending on {@code platform-api}; the
 * audit-log controller maps each record to its wire DTO.
 */
public final class AuditLogReadModels {

    private AuditLogReadModels() {
    }

    /**
     * One row in the audit-log viewer.
     *
     * @param id audit log identifier
     * @param companyId owning company, or {@code null} for cross-company actions
     * @param companyName owning company display name ("" when unresolved)
     * @param userId acting user id, or {@code null}
     * @param action audited action label
     * @param entityType audited entity type, or {@code null}
     * @param entityId audited entity id, or {@code null}
     * @param payloadBefore entity state before the action, or {@code null}
     * @param payloadAfter entity state after the action, or {@code null}
     * @param ipAddress originating IP address, or {@code null}
     * @param createdAt UTC creation timestamp
     */
    public record AuditLogRow(
            Long id,
            UUID companyId,
            String companyName,
            UUID userId,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> payloadBefore,
            Map<String, Object> payloadAfter,
            String ipAddress,
            OffsetDateTime createdAt) {
    }
}
