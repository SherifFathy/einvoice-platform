package com.einvoice.core.repository.shared;

import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
