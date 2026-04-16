package com.einvoice.api.audit;

import com.einvoice.api.audit.dto.AuditLogResponse;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.AuditLog;
import com.einvoice.core.service.AuditService;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for querying append-only audit logs. */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    /**
     * Creates the audit log controller.
     *
     * @param auditService the audit service
     */
    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Lists audit logs for the current tenant with optional filters.
     *
     * @param pageable pagination parameters
     * @param entityType optional entity type filter
     * @param entityId optional entity identifier filter
     * @param from optional start of date range
     * @param to optional end of date range
     * @return a page of audit log responses
     */
    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Page<AuditLogResponse>> listAuditLogs(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        Long companyId = TenantContext.getCurrentTenantId();
        Page<AuditLog> logs = auditService.findFiltered(
                companyId, entityType, entityId, from, to, pageable);
        return ResponseEntity.ok(logs.map(this::toResponse));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getCompanyId(),
                log.getUserId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getPayloadBefore(),
                log.getPayloadAfter(),
                log.getIpAddress(),
                log.getTimestamp());
    }
}
