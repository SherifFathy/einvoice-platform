package com.einvoice.api.audit.dto;

import java.time.OffsetDateTime;

/** Filter parameters for querying audit logs. */
public record AuditLogFilterRequest(
        String entityType,
        String entityId,
        OffsetDateTime from,
        OffsetDateTime to) {}
