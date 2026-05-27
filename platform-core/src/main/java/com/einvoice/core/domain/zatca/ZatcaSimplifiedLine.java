package com.einvoice.core.domain.zatca;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "item_net_price", precision = 18, scale = 5)
    private BigDecimal itemNetPrice;

    @Column(name = "item_gross_price", precision = 18, scale = 5)
    private BigDecimal itemGrossPrice;

    @Column(name = "item_price_discount", precision = 18, scale = 5)
    private BigDecimal itemPriceDiscount;

    @Column(name = "item_price_base_quantity", precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal itemPriceBaseQuantity = BigDecimal.ONE;

    @Column(name = "item_price_base_quantity_unit", length = 127)
    private String itemPriceBaseQuantityUnit;

    @Column(name = "vat_inclusive_amount", precision = 18, scale = 2)
    private BigDecimal vatInclusiveAmount;

    @Column(name = "line_extension_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal lineExtensionAmount = BigDecimal.ZERO;

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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "line")
    @Builder.Default
    private List<ZatcaSimplifiedLineAllowance> allowances = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public BigDecimal allowanceTotal() {
        return allowances.stream()
                .map(ZatcaSimplifiedLineAllowance::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
