package com.einvoice.core.domain.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "eta_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaConfig {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "authority_environment_id", nullable = false)
    private Short authorityEnvironmentId;

    @Column(name = "client_id", nullable = false, columnDefinition = "TEXT")
    private String clientId;

    @Column(name = "client_secret_1", nullable = false, columnDefinition = "TEXT")
    private String clientSecret1;

    @Column(name = "client_secret_2", nullable = false, columnDefinition = "TEXT")
    private String clientSecret2;

    @Column(name = "token_name", columnDefinition = "TEXT")
    private String tokenName;

    @Column(name = "token_pass", columnDefinition = "TEXT")
    private String tokenPass;

    @Column(name = "submission_url", nullable = false, columnDefinition = "TEXT")
    private String submissionUrl;

    @Column(name = "token_url", nullable = false, columnDefinition = "TEXT")
    private String tokenUrl;

    @Column(name = "pos_serial", columnDefinition = "TEXT")
    private String posSerial;

    @Column(name = "pos_os_version", columnDefinition = "TEXT")
    private String posOsVersion;

    @Column(name = "pos_model", columnDefinition = "TEXT")
    private String posModel;

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
