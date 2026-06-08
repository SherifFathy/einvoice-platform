package com.einvoice.api.audit;

import com.einvoice.api.audit.dto.AuditLogRowDto;
import com.einvoice.core.service.audit.AuditLogQuery;
import com.einvoice.core.service.audit.AuditLogQueryService;
import com.einvoice.core.service.audit.AuditLogReadModels.AuditLogRow;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Wave 9 authority-scoped audit-log read endpoint (US4). Company-less and
 * environment-scoped per ADR-001: reachable in {@code AUTHORITY_SCOPED} (and
 * {@code OPERATIONAL_MODE}), with no {@code @RequiresPermission} VIEW gate and
 * no {@code COMPANY_CONTEXT_REQUIRED} guard. The active
 * {@code authority_environment_id} is the only hard boundary;
 * {@code ADMIN_MODE} is rejected upstream by the tenant filter, and
 * cross-company reads within the environment are intended (the owning company
 * is identified on every row).
 *
 * <p>Returns a Spring {@link Page} of {@link AuditLogRowDto} whose serialized
 * shape ({@code content}, {@code totalElements}, {@code totalPages},
 * {@code number}, {@code size}) is consumed unchanged by the existing
 * {@code PageResponse<AuditLogResponse>} frontend. Default sort:
 * {@code createdAt DESC} (newest first). Query parameters follow what the
 * existing frontend sends: {@code page}, {@code size}, {@code entityType},
 * {@code entityId}, {@code from}, {@code to}, plus an optional
 * {@code companyId} single-company narrowing within the environment.
 *
 * <p>Append-only: this controller exposes only {@code GET} (Constitution
 * IX.3/XXI.4); no {@code PUT}/{@code PATCH}/{@code DELETE} is served here.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogReadController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Upper bound on page size to protect the read query. */
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogQueryService service;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the controller with its audit-log query service.
     *
     * @param service the audit-log query service
     * @param objectMapper Jackson mapper for payload JSON serialization
     */
    public AuditLogReadController(AuditLogQueryService service,
            ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists audit-log entries across all companies in the active authority
     * environment.
     *
     * @param filterCompanyId optional single-company narrowing within the env
     * @param entityType optional audited entity-type filter
     * @param entityId optional audited entity-id filter
     * @param from inclusive {@code createdAt} lower bound (ISO-8601 or date)
     * @param to inclusive {@code createdAt} upper bound (ISO-8601 or date)
     * @param page zero-based page index
     * @param size page size
     * @return a paged response of audit-log rows
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogRowDto>> list(
            @RequestParam(name = "companyId", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE + "")
                    int size) {
        Short env = TenantContext.getAuthorityEnvironmentId();
        AuditLogQuery query = new AuditLogQuery(
                env,
                filterCompanyId,
                blankToNull(entityType),
                blankToNull(entityId),
                parseDateFrom(from),
                parseDateTo(to));
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogRow> result = service.list(query, pageable);
        return ResponseEntity.ok(result.map(this::toDto));
    }

    /**
     * Maps an audit-log row read-model to its wire DTO, serializing the
     * payload maps to JSON text for the expandable detail view.
     *
     * @param row the row read-model
     * @return the row DTO
     */
    private AuditLogRowDto toDto(AuditLogRow row) {
        return new AuditLogRowDto(
                row.id(),
                toStr(row.companyId()),
                row.companyName(),
                toStr(row.userId()),
                row.action(),
                row.entityType(),
                row.entityId(),
                toJson(row.payloadBefore()),
                toJson(row.payloadAfter()),
                row.ipAddress(),
                row.createdAt());
    }

    /**
     * Serializes a payload map to JSON text so the existing frontend detail
     * view ({@code JSON.parse(json)}) keeps working unchanged.
     *
     * @param payload the payload map, or {@code null}
     * @return the JSON text, or {@code null}
     */
    private String toJson(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * Renders a UUID as a wire string, or {@code null}.
     *
     * @param id the uuid, or {@code null}
     * @return the string form, or {@code null}
     */
    private static String toStr(UUID id) {
        return id != null ? id.toString() : null;
    }

    /**
     * Treats a blank parameter as no filter.
     *
     * @param value the raw parameter
     * @return the trimmed value, or {@code null} when blank
     */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Parses an optional {@code createdAt} lower bound, accepting a full
     * ISO-8601 offset date-time or a plain date (taken as start-of-day UTC).
     *
     * @param value the raw parameter, or {@code null}/blank
     * @return the lower bound, or {@code null}
     */
    private static OffsetDateTime parseDateFrom(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException full) {
            try {
                return LocalDate.parse(value)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toOffsetDateTime();
            } catch (DateTimeParseException dateOnly) {
                throw badRequest(value);
            }
        }
    }

    /**
     * Parses an optional {@code createdAt} upper bound, accepting a full
     * ISO-8601 offset date-time or a plain date (taken as end-of-day UTC so the
     * inclusive bound captures the whole day).
     *
     * @param value the raw parameter, or {@code null}/blank
     * @return the upper bound, or {@code null}
     */
    private static OffsetDateTime parseDateTo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException full) {
            try {
                return LocalDate.parse(value)
                        .atTime(LocalTime.MAX)
                        .atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException dateOnly) {
                throw badRequest(value);
            }
        }
    }

    /**
     * Builds a {@code 400 Bad Request} for an unparseable date parameter.
     *
     * @param value the offending raw value
     * @return a bad-request exception to throw
     */
    private static ResponseStatusException badRequest(String value) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid date filter: " + value);
    }
}
