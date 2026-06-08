package com.einvoice.core.service.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable, parsed parameter bundle for the authority-scoped audit-log query
 * (Wave 9, US4) under the company-less {@code AUTHORITY_SCOPED} model
 * (ADR-001).
 *
 * <p>The active {@code authority_environment_id} ({@link #envId()}) is the
 * single hard isolation boundary and is always applied; it is resolved from
 * {@code TenantContext} and is never user-overridable. {@link #companyId()}
 * optionally narrows to a single company within the environment (the default
 * is cross-company). The remaining fields mirror the existing audit-log
 * filters (entity type/id and the {@code createdAt} window).
 *
 * @param envId the active authority environment (the hard isolation boundary)
 * @param companyId optional single-company narrowing within the env
 * @param entityType optional entity-type filter
 * @param entityId optional entity-id filter
 * @param dateFrom inclusive {@code createdAt} lower bound, or {@code null}
 * @param dateTo inclusive {@code createdAt} upper bound, or {@code null}
 */
public record AuditLogQuery(
        Short envId,
        UUID companyId,
        String entityType,
        String entityId,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo) {
}
