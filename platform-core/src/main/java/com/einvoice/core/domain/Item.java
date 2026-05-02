package com.einvoice.core.domain;

import com.einvoice.core.domain.enums.AuthorityScope;
import com.einvoice.core.domain.enums.VatCategory;
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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Master-data item (product or service) owned by a company. */
@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "lov_context_id", nullable = false)
    private Long lovContextId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name_ar")
    private String nameAr;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "unit_of_measure", nullable = false)
    private String unitOfMeasure;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "vat_category", nullable = false)
    @Enumerated(EnumType.STRING)
    private VatCategory vatCategory;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "description")
    private String description;

    @Column(name = "authority_scope")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AuthorityScope authorityScope = AuthorityScope.BOTH;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
