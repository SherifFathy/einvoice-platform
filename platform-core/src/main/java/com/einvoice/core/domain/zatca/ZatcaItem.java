package com.einvoice.core.domain.zatca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "zatca_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZatcaItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "authority_environment_id", nullable = false)
    private Short authorityEnvironmentId;

    @Column(name = "internal_code", nullable = false, length = 100)
    private String internalCode;

    @Column(name = "item_code", length = 100)
    private String itemCode;

    @Column(name = "name_ar")
    private String nameAr;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "unit_type", length = 50)
    private String unitType;

    @Column(name = "unit_price", precision = 18, scale = 5)
    private BigDecimal unitPrice;

    @Column(name = "vat_category", nullable = false, length = 10)
    private String vatCategory;

    @Column(name = "vat_rate", precision = 8, scale = 2)
    private BigDecimal vatRate;

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
