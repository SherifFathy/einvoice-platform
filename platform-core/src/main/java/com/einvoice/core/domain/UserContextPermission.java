package com.einvoice.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity for fine-grained user permissions scoped to company and LOV context. */
@Entity
@Table(name = "user_context_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserContextPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lov_context_id", nullable = false)
    private LovContext lovContext;

    @Column(name = "permission", nullable = false, length = 30)
    private String permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

    @Column(name = "granted_at", updatable = false)
    private OffsetDateTime grantedAt;

    @PrePersist
    protected void onCreate() {
        grantedAt = OffsetDateTime.now();
    }
}
