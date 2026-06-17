package com.einvoice.api.submission;

import com.einvoice.api.submission.dto.SubmissionLogRowDto;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.service.submission.SubmissionLogQuery;
import com.einvoice.core.service.submission.SubmissionLogQueryService;
import com.einvoice.core.service.submission.SubmissionLogReadModels.SubmissionLogRow;
import com.einvoice.security.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
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
 * Wave 9 unified submission-log endpoint spanning all four document classes
 * (ETA Invoice/Receipt, ZATCA Standard/Simplified). Company-less and
 * environment-scoped per ADR-001: reachable in {@code AUTHORITY_SCOPED} (and
 * {@code OPERATIONAL_MODE}), with no {@code @RequiresPermission} VIEW gate and
 * no {@code COMPANY_CONTEXT_REQUIRED} guard. Environment isolation is the only
 * hard boundary; {@code ADMIN_MODE} is rejected upstream by the tenant filter.
 *
 * <p>Returns the Wave-9 paged envelope {@code {items, page, size,
 * totalElements}} so the submission log matches the other Wave-9 read
 * controllers (the frontend paginator derives {@code totalPages} from
 * {@code totalElements / size}). Default sort: {@code submittedAt DESC}.
 */
@RestController
@RequestMapping("/api/submission-log")
public class SubmissionLogController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Upper bound on page size to protect the large-list query (SC-008). */
    private static final int MAX_PAGE_SIZE = 200;

    private final SubmissionLogQueryService service;

    /**
     * Constructs the controller with its submission-log query service.
     *
     * @param service the submission-log query service
     */
    public SubmissionLogController(SubmissionLogQueryService service) {
        this.service = service;
    }

    /**
     * Lists submission attempts across all four classes in the active
     * authority environment.
     *
     * @param filterCompanyId optional single-company narrowing within the env
     * @param transactionType optional {@code INVOICE/RECEIPT/STANDARD/SIMPLIFIED}
     * @param outcome optional outcome — {@code SUCCESS/REJECTED/ERROR/TIMEOUT/
     *                AMBIGUOUS/IN_FLIGHT} ({@code IN_FLIGHT} matches null result)
     * @param dateFrom optional inclusive {@code submittedAt} lower bound (ISO-8601)
     * @param dateTo optional inclusive {@code submittedAt} upper bound (ISO-8601)
     * @param page zero-based page index
     * @param size page size
     * @return a paged envelope of submission-log rows
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "companyId", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE + "")
                    int size) {
        Short env = TenantContext.getAuthorityEnvironmentId();
        ParsedOutcome parsedOutcome = parseOutcome(outcome);
        SubmissionLogQuery query = new SubmissionLogQuery(
                env,
                filterCompanyId,
                parseTransactionType(transactionType),
                parsedOutcome.result(),
                parsedOutcome.inFlight(),
                parseDateFrom(dateFrom),
                parseDateTo(dateTo));
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "submittedAt"));
        Page<SubmissionLogRow> result = service.list(query, pageable);
        List<SubmissionLogRowDto> items = result.getContent().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(Map.of(
                "items", items,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /**
     * Maps a submission-log row read-model to its wire DTO.
     *
     * @param row the row read-model
     * @return the row DTO
     */
    private SubmissionLogRowDto toDto(SubmissionLogRow row) {
        return new SubmissionLogRowDto(
                row.attemptId(),
                row.companyId(),
                row.companyName(),
                row.transactionType(),
                row.documentId(),
                row.attemptNumber(),
                row.outcome(),
                row.statusCode(),
                row.errorSummary(),
                row.submittedAt(),
                row.completedAt(),
                row.submittedBy());
    }

    /**
     * Parses an optional transaction-type parameter.
     *
     * @param value the raw parameter, or {@code null}/blank for no filter
     * @return the transaction type, or {@code null}
     */
    private static TransactionType parseTransactionType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TransactionType.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw badRequest("transactionType", value);
        }
    }

    /**
     * Parses an optional outcome parameter, special-casing {@code IN_FLIGHT}
     * (which matches a null result rather than a {@link SubmissionResult}).
     *
     * @param value the raw parameter, or {@code null}/blank for no filter
     * @return the parsed outcome (result + in-flight flag)
     */
    private static ParsedOutcome parseOutcome(String value) {
        if (value == null || value.isBlank()) {
            return new ParsedOutcome(null, false);
        }
        String trimmed = value.trim();
        if ("IN_FLIGHT".equalsIgnoreCase(trimmed)) {
            return new ParsedOutcome(null, true);
        }
        try {
            return new ParsedOutcome(SubmissionResult.valueOf(trimmed), false);
        } catch (IllegalArgumentException ex) {
            throw badRequest("outcome", value);
        }
    }

    /**
     * Parses an optional {@code submittedAt} lower bound, accepting a full
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
                throw badRequest("dateFrom", value);
            }
        }
    }

    /**
     * Parses an optional {@code submittedAt} upper bound, accepting a full
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
                throw badRequest("dateTo", value);
            }
        }
    }

    /**
     * Builds a {@code 400 Bad Request} for an unparseable filter parameter.
     *
     * @param param the offending parameter name
     * @param value the offending raw value
     * @return a bad-request exception to throw
     */
    private static ResponseStatusException badRequest(String param, String value) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid " + param + ": " + value);
    }

    /** Holder for a parsed outcome filter. */
    private record ParsedOutcome(SubmissionResult result, boolean inFlight) {
    }
}
