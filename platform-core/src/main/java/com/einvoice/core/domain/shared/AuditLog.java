package com.einvoice.core.domain.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Immutable audit trail for every state-changing action.
 * Append-only (Constitution IX). documentId has no DB FK; orchestrator enforces (XI.6).
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "authority_environment_id")
    private Short authorityEnvironmentId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", columnDefinition = "TEXT")
    private String entityId;

    @Column(name = "payload_before", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payloadBefore;

    @Column(name = "payload_after", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payloadAfter;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
