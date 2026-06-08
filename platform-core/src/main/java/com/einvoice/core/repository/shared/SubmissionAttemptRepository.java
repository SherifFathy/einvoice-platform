package com.einvoice.core.repository.shared;

import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Repository for submission attempts with tenant-scoped queries. */
public interface SubmissionAttemptRepository extends WriteOnlyRepository<SubmissionAttempt, UUID> {

    @Query("SELECT MAX(sa.attemptNumber) FROM SubmissionAttempt sa "
            + "WHERE sa.documentId = :documentId")
    Optional<Integer> findMaxAttemptNumber(@Param("documentId") UUID documentId);

    @Query("SELECT sa FROM SubmissionAttempt sa "
            + "WHERE sa.documentId = :documentId "
            + "AND sa.companyId = :companyId "
            + "AND sa.transactionType = :transactionType "
            + "ORDER BY sa.attemptNumber ASC")
    List<SubmissionAttempt> findByDocumentIdAndTenant(
            @Param("documentId") UUID documentId,
            @Param("companyId") UUID companyId,
            @Param("transactionType") TransactionType transactionType);

    /**
     * Returns the latest submission attempt for each document whose latest
     * result is one of {@code results}, restricted to a company set in an
     * environment. Drives the dashboard failed-count rule (latest
     * {@code ERROR/TIMEOUT}). A document whose latest attempt is still in flight
     * (null result) is excluded because it is not the latest-finalized failure.
     *
     * @param authorityEnvironmentId the active authority environment
     * @param companyIds accessible companies in the environment
     * @param results results that mark a document as failed by attempt
     * @return the latest failed attempt per matching document
     */
    @Query("SELECT sa FROM SubmissionAttempt sa "
            + "WHERE sa.authorityEnvironmentId = :envId "
            + "AND sa.companyId IN :companyIds "
            + "AND sa.result IN :results "
            + "AND sa.attemptNumber = (SELECT MAX(sa2.attemptNumber) "
            + "FROM SubmissionAttempt sa2 "
            + "WHERE sa2.documentId = sa.documentId "
            + "AND sa2.companyId = sa.companyId "
            + "AND sa2.authorityEnvironmentId = sa.authorityEnvironmentId)")
    List<SubmissionAttempt> findLatestAttemptsByResult(
            @Param("envId") Short authorityEnvironmentId,
            @Param("companyIds") Collection<UUID> companyIds,
            @Param("results") Collection<SubmissionResult> results);

    /**
     * Returns the most recent submission attempts across a company set in an
     * environment, newest first, capped by the supplied pageable (dashboard uses
     * a page size of 10).
     *
     * @param authorityEnvironmentId the active authority environment
     * @param companyIds accessible companies in the environment
     * @param pageable limit and sort (sort applied is {@code submittedAt DESC})
     * @return the newest submission attempts
     */
    @Query("SELECT sa FROM SubmissionAttempt sa "
            + "WHERE sa.authorityEnvironmentId = :envId "
            + "AND sa.companyId IN :companyIds "
            + "ORDER BY sa.submittedAt DESC")
    List<SubmissionAttempt> findRecentAttempts(
            @Param("envId") Short authorityEnvironmentId,
            @Param("companyIds") Collection<UUID> companyIds,
            Pageable pageable);

    /**
     * Counts submission attempts in an environment within a half-open UTC
     * window. Drives the Admin-Mode {@code totalSubmissionsToday} stat
     * (Constitution VII.3 — counts only). Bind {@code [from, to)} from
     * {@link com.einvoice.core.util.UtcDateRange#todayRange} so the boundary is
     * always the UTC day, never server-local time.
     *
     * @param authorityEnvironmentId the active authority environment
     * @param from inclusive UTC lower bound (start of UTC day)
     * @param to   exclusive UTC upper bound (start of next UTC day)
     * @return the number of submission attempts submitted in the window
     */
    @Query("SELECT COUNT(sa) FROM SubmissionAttempt sa "
            + "WHERE sa.authorityEnvironmentId = :envId "
            + "AND sa.submittedAt >= :from AND sa.submittedAt < :to")
    long countByAuthorityEnvironmentIdAndSubmittedAtBetween(
            @Param("envId") Short authorityEnvironmentId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Modifying
    @Transactional
    @Query("UPDATE SubmissionAttempt sa SET sa.result = :result, sa.statusCode = :statusCode, "
            + "sa.errorSummary = :errorSummary, sa.responsePayloadRef = :responsePayloadRef, "
            + "sa.completedAt = :completedAt WHERE sa.id = :id")
    void finalizeAttempt(@Param("id") UUID id,
            @Param("result") SubmissionResult result,
            @Param("statusCode") Integer statusCode,
            @Param("errorSummary") String errorSummary,
            @Param("responsePayloadRef") String responsePayloadRef,
            @Param("completedAt") OffsetDateTime completedAt);
}
