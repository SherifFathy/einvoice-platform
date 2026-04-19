package com.einvoice.core.domain;

import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.SubmissionResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Records each individual attempt to submit an invoice to a tax authority. */
@Entity
@Table(name = "submission_attempts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_submission_attempt",
        columnNames = {"invoice_id", "attempt_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false, length = 25)
    @Enumerated(EnumType.STRING)
    private Environment environment;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private Authority authority;

    @Column(name = "request_payload_ref", columnDefinition = "text")
    private String requestPayloadRef;

    @Column(name = "response_payload_ref", columnDefinition = "text")
    private String responsePayloadRef;

    @Column(name = "signed_artifact_ref", columnDefinition = "text")
    private String signedArtifactRef;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SubmissionResult result;

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = OffsetDateTime.now();
        }
    }
}
