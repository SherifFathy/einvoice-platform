package com.einvoice.core.domain;

import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceResetPolicy;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA entity representing authority configuration for a branch. */
@Entity
@Table(name = "authority_configs", uniqueConstraints = {
    @UniqueConstraint(name = "uq_authority_config",
        columnNames = {"branch_id", "authority", "environment"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorityConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Authority authority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Environment environment;

    @Column(name = "credentials_encrypted", columnDefinition = "bytea")
    private byte[] credentialsEncrypted;

    @Column(name = "certificate_encrypted", columnDefinition = "bytea")
    private byte[] certificateEncrypted;

    @Column(name = "csid_encrypted", columnDefinition = "bytea")
    private byte[] csidEncrypted;

    @Column(name = "private_key_encrypted", columnDefinition = "bytea")
    private byte[] privateKeyEncrypted;

    @Column(name = "token_data_encrypted", columnDefinition = "bytea")
    private byte[] tokenDataEncrypted;

    @Column(name = "certificate_expiry_date")
    private OffsetDateTime certificateExpiryDate;

    @Column(name = "invoice_counter")
    @Builder.Default
    private Long invoiceCounter = 0L;

    @Column(name = "previous_invoice_hash", columnDefinition = "text")
    private String previousInvoiceHash;

    @Column(name = "invoice_prefix", length = 20)
    private String invoicePrefix;

    @Column(name = "invoice_starting_number")
    @Builder.Default
    private Long invoiceStartingNumber = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_reset_policy", length = 20)
    @Builder.Default
    private InvoiceResetPolicy invoiceResetPolicy = InvoiceResetPolicy.NEVER;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enabled_document_types", columnDefinition = "jsonb")
    @Builder.Default
    private String enabledDocumentTypes = "[]";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
