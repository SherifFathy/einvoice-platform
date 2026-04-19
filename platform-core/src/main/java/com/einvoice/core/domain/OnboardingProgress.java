package com.einvoice.core.domain;

import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.OnboardingStep;
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

/** Tracks step-by-step progress of ZATCA onboarding per branch. */
@Entity
@Table(name = "onboarding_progress", uniqueConstraints = {
    @UniqueConstraint(name = "uq_onboarding_progress",
        columnNames = {"branch_id", "authority", "environment"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private Authority authority;

    @Column(nullable = false, length = 25)
    @Enumerated(EnumType.STRING)
    private Environment environment;

    @Column(name = "current_step", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OnboardingStep currentStep = OnboardingStep.NOT_STARTED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_data", columnDefinition = "jsonb")
    private String stepData;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
