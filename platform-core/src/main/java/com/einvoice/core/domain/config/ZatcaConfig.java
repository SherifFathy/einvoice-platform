package com.einvoice.core.domain.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Javadoc. */
@Entity
@Table(name = "zatca_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZatcaConfig {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "authority_environment_id", nullable = false)
    private Short authorityEnvironmentId;

    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String privateKey;

    @Column(name = "device_uuid", nullable = false, columnDefinition = "TEXT")
    private String deviceUuid;

    @Column(name = "csr", nullable = false, columnDefinition = "TEXT")
    private String csr;

    @Column(name = "compliance_certificate", nullable = false, columnDefinition = "TEXT")
    private String complianceCertificate;

    @Column(name = "compliance_api_secret", nullable = false, columnDefinition = "TEXT")
    private String complianceApiSecret;

    @Column(name = "production_certificate", columnDefinition = "TEXT")
    private String productionCertificate;

    @Column(name = "production_api_secret", columnDefinition = "TEXT")
    private String productionApiSecret;

    @Column(name = "base_url", length = 255)
    private String baseUrl;

    @Column(name = "certificate_expiry_date")
    private LocalDate certificateExpiryDate;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
