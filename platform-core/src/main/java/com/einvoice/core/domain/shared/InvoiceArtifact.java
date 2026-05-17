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
 * Immutable record of a payload tied to a document.
 * Append-only (Constitution XXI). documentId has no DB FK; orchestrator enforces (XI.6).
 */
@Entity
@Table(name = "invoice_artifacts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceArtifact {

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

    @Column(name = "artifact_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ArtifactType artifactType;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "content_hash", columnDefinition = "TEXT", nullable = false)
    private String contentHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
