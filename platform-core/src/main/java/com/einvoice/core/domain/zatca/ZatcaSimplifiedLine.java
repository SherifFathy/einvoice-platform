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

/** JPA entity for ZATCA simplified invoice line items. */
@Entity
@Table(name = "zatca_simplified_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZatcaSimplifiedLine {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "header_id", nullable = false)
    private ZatcaSimplifiedHeader header;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "item_code", length = 100)
    private String itemCode;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit_type", length = 50)
    private String unitType;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 5)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 5)
    private BigDecimal unitPrice;

    @Column(name = "line_extension_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal lineExtensionAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "allowance_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal allowanceAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "vat_category_code", nullable = false, length = 5)
    private String vatCategoryCode;

    @Column(name = "vat_rate", precision = 8, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "exemption_reason_code", length = 10)
    private String exemptionReasonCode;

    @Column(name = "exemption_reason_text", columnDefinition = "TEXT")
    private String exemptionReasonText;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
