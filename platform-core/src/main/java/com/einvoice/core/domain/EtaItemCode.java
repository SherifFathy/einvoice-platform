package com.einvoice.core.domain;

import com.einvoice.core.domain.enums.EtaItemCodeStatus;
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

/** Tracks item code registrations with the Egyptian Tax Authority. */
@Entity
@Table(name = "eta_item_codes", uniqueConstraints = {
    @UniqueConstraint(name = "uq_eta_item_code",
        columnNames = {"company_id", "item_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaItemCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "item_code", nullable = false, length = 100)
    private String itemCode;

    @Column(name = "code_type", nullable = false, length = 50)
    private String codeType;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EtaItemCodeStatus status = EtaItemCodeStatus.PENDING;

    @Column(name = "eta_code_id", length = 100)
    private String etaCodeId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
