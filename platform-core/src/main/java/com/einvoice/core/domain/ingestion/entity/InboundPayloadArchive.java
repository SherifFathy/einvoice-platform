package com.einvoice.core.domain.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.type.SqlTypes;

/**
 * Append-only archive of every inbound integration request payload (FR-OBS-003).
 * The only permitted mutation is {@code outcome} (per data-model.md §1).
 * {@code companyId} and {@code authorityEnvironmentId} are updated via
 * JPQL {@code @Modifying} queries on the repository so that the entity
 * itself remains immutable beyond outcome.
 */
@Entity
@Table(name = "inbound_payload_archive")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundPayloadArchive {

    @Id
    private UUID id;

    @Column(name = "endpoint", nullable = false, length = 120)
    private String endpoint;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "body", nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(SqlTypes.JSON)
    private String body;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "authority_environment_id")
    private Short authorityEnvironmentId;

    @Column(name = "outcome")
    private Short outcome;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
    }

    public void setOutcome(Short outcome) {
        this.outcome = outcome;
    }
}
