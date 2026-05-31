package com.einvoice.core.domain.zatca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** JPA entity for ZATCA simplified per-rate tax subtotal (BG-23). */
@Entity
@Table(name = "zatca_simplified_tax_subtotals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZatcaSimplifiedTaxSubtotal {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "header_id", nullable = false)
    private ZatcaSimplifiedHeader header;

    @Column(name = "vat_category_code", length = 5)
    private String vatCategoryCode;

    @Column(name = "vat_rate", precision = 8, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "taxable_amount", precision = 18, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "exemption_reason_code", length = 10)
    private String exemptionReasonCode;

    @Column(name = "exemption_reason_text", length = 127)
    private String exemptionReasonText;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
