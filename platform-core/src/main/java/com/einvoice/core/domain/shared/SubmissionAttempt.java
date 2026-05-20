package com.einvoice.core.domain.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Single transmission of a document to any authority.
 * Append-only after result is recorded (research Decision 4).
 * documentId has no DB FK; the orchestrator (T055) enforces existence (Constitution XI.6).
 */
@Entity
@Table(name = "submission_attempts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "authority_environment_id", nullable = false)
    private Short authorityEnvironmentId;

    @Column(name = "transaction_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "chain_counter_snapshot")
    private Long chainCounterSnapshot;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private SubmissionResult result;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "request_payload_ref", columnDefinition = "TEXT")
    private String requestPayloadRef;

    @Column(name = "response_payload_ref", columnDefinition = "TEXT")
    private String responsePayloadRef;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    void finalizeAttempt(SubmissionResult result, Integer statusCode,
            String errorSummary, String responsePayloadRef, OffsetDateTime completedAt) {
        this.result = result;
        this.statusCode = statusCode;
        this.errorSummary = errorSummary;
        this.responsePayloadRef = responsePayloadRef;
        this.completedAt = completedAt;
    }
}
