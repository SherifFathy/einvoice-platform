package com.einvoice.api.audit.dto;

import java.time.OffsetDateTime;

/**
 * One row in the authority-scoped audit-log viewer (Wave 9, US4). Mirrors the
 * existing {@code AuditLogResponse} shape consumed by
 * {@code frontend/src/app/logs/logs.component.ts} so the read endpoint can be
 * served with no frontend rewrite.
 *
 * <p>Field mappings reconciled against the frontend source of truth:
 * <ul>
 *   <li>backend {@code createdAt} &rarr; wire {@code timestamp};</li>
 *   <li>backend {@code companyId}/{@code userId} (UUID) &rarr; wire strings;</li>
 *   <li>{@code companyId}/{@code companyName} added so the cross-company
 *       viewer can identify the owning company per row (FR-014);</li>
 *   <li>{@code payloadBefore}/{@code payloadAfter} (JSONB maps) &rarr; JSON
 *       text strings so the existing expandable detail view's
 *       {@code JSON.parse(json)} keeps working unchanged.</li>
 * </ul>
 *
 * @param id audit log identifier
 * @param companyId owning company id, or {@code null}
 * @param companyName owning company display name ("" when unresolved)
 * @param userId acting user id, or {@code null}
 * @param action audited action label
 * @param entityType audited entity type, or {@code null}
 * @param entityId audited entity id, or {@code null}
 * @param payloadBefore entity state before the action (JSON text), or {@code null}
 * @param payloadAfter entity state after the action (JSON text), or {@code null}
 * @param ipAddress originating IP address, or {@code null}
 * @param timestamp UTC creation timestamp (ISO-8601)
 */
public record AuditLogRowDto(
        Long id,
        String companyId,
        String companyName,
        String userId,
        String action,
        String entityType,
        String entityId,
        String payloadBefore,
        String payloadAfter,
        String ipAddress,
        OffsetDateTime timestamp) {
}
