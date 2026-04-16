package com.einvoice.api.audit.dto;

import java.time.OffsetDateTime;

/** Response body for an audit log entry. */
public record AuditLogResponse(
        Long id,
        Long companyId,
        Long userId,
        String action,
        String entityType,
        String entityId,
        String payloadBefore,
        String payloadAfter,
        String ipAddress,
        OffsetDateTime timestamp) {}
