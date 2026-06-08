package com.einvoice.core.service.submission;

import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable, parsed parameter bundle for the unified submission-log query.
 *
 * <p>The outcome is split into {@link #outcomeResult()} (a finalized
 * {@link SubmissionResult}, or {@code null}) and {@link #inFlight()} (true only
 * when filtering for in-flight attempts). When {@code inFlight} is true the
 * result filter is {@code result IS NULL}; otherwise, when {@code outcomeResult}
 * is non-null, the filter is {@code result = outcomeResult}; when both are
 * null no outcome filter is applied. The two never combine.
 *
 * @param envId the active authority environment (the hard isolation boundary)
 * @param filterCompanyId optional single-company narrowing within the env
 * @param transactionType optional transaction-class filter
 * @param outcomeResult optional finalized-outcome filter, or {@code null}
 * @param inFlight whether to filter in-flight ({@code result IS NULL}) attempts
 * @param dateFrom inclusive {@code submittedAt} lower bound, or {@code null}
 * @param dateTo inclusive {@code submittedAt} upper bound, or {@code null}
 */
public record SubmissionLogQuery(
        Short envId,
        UUID filterCompanyId,
        TransactionType transactionType,
        SubmissionResult outcomeResult,
        boolean inFlight,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo) {
}
