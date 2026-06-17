package com.einvoice.core.repository.support;

import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification factories for {@link SubmissionAttempt} queries that drive the
 * unified submission log. Environment and company scoping reuse
 * {@link OperationalRepositorySupport}; the factories here cover the
 * submission-log-specific filters (transaction class, outcome / in-flight, and
 * the {@code submittedAt} window).
 *
 * <p>The {@code IN_FLIGHT} outcome is represented at the query level as
 * {@code result IS NULL} — it is never bound as a {@link SubmissionResult}
 * value (no such enum constant exists).
 */
public final class SubmissionAttemptSpecifications {

    private SubmissionAttemptSpecifications() {
    }

    /**
     * Filters by transaction class.
     *
     * @param type the transaction class
     * @return a specification matching the given transaction class
     */
    public static Specification<SubmissionAttempt> transactionTypeEquals(
            TransactionType type) {
        return (Root<SubmissionAttempt> root, CriteriaQuery<?> query,
                CriteriaBuilder cb) ->
                cb.equal(root.get("transactionType"), type);
    }

    /**
     * Filters by finalized outcome.
     *
     * @param result the finalized submission result
     * @return a specification matching the given result
     */
    public static Specification<SubmissionAttempt> resultEquals(
            SubmissionResult result) {
        return (Root<SubmissionAttempt> root, CriteriaQuery<?> query,
                CriteriaBuilder cb) ->
                cb.equal(root.get("result"), result);
    }

    /**
     * Filters in-flight attempts — those whose result has not been finalized
     * ({@code result IS NULL}).
     *
     * @return a specification matching in-flight attempts
     */
    public static Specification<SubmissionAttempt> inFlight() {
        return (Root<SubmissionAttempt> root, CriteriaQuery<?> query,
                CriteriaBuilder cb) ->
                cb.isNull(root.get("result"));
    }

    /**
     * Filters by a closed {@code submittedAt} window. Either bound may be null
     * to leave that side open.
     *
     * @param from inclusive lower bound on {@code submittedAt} (nullable)
     * @param to inclusive upper bound on {@code submittedAt} (nullable)
     * @return a specification constraining {@code submittedAt} to the window
     */
    public static Specification<SubmissionAttempt> submittedAtBetween(
            OffsetDateTime from, OffsetDateTime to) {
        return (Root<SubmissionAttempt> root, CriteriaQuery<?> query,
                CriteriaBuilder cb) -> {
            Predicate pred = cb.conjunction();
            if (from != null) {
                pred = cb.and(pred, cb.greaterThanOrEqualTo(
                        root.get("submittedAt"), from));
            }
            if (to != null) {
                pred = cb.and(pred, cb.lessThanOrEqualTo(
                        root.get("submittedAt"), to));
            }
            return pred;
        };
    }
}
